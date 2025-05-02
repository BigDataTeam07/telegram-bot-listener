package org.iss.bigdata.practice.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client to interact with Elasticsearch for music recommendations
 */
public class ElasticsearchClient {
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchClient.class);
    private static final ElasticsearchClient INSTANCE = new ElasticsearchClient();
    private final HttpClient httpClient;
    private final String elasticsearchUrl;
    private final ObjectMapper objectMapper;

    private ElasticsearchClient() {
        // Configure timeout settings
        httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        // Default to connecting to our ELK stack in Kubernetes
        this.elasticsearchUrl = System.getenv().getOrDefault("ELASTICSEARCH_URL",
                "http://elasticsearch.elk-ns.svc.cluster.local:9200");
        this.objectMapper = new ObjectMapper();

        logger.info("ElasticsearchClient initialized with URL: {}", elasticsearchUrl);
    }

    public static ElasticsearchClient getInstance() {
        return INSTANCE;
    }

    /**
     * Find music recommendations for a specific user ID
     *
     * @param userId The Telegram user ID to search for
     * @return List of recommended song titles or empty list if none found
     */
    public List<String> getMusicRecommendationsForUser(Long userId) {
        List<String> recommendations = new ArrayList<>();

        try {
            String requestBody = "{\n" +
                    "  \"size\": 2,\n" +
                    "  \"query\": {\n" +
                    "    \"term\": {\n" +
                    "      \"userId.keyword\": \"" + userId + "\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            String searchUrl = elasticsearchUrl + "/recommendations-*/_search";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                JsonNode hits = jsonResponse.path("hits").path("hits");

                // Extract song titles directly from the hits
                if (hits.isArray() && hits.size() > 0) {
                    for (JsonNode hit : hits) {
                        JsonNode source = hit.path("_source");
                        // The title is directly in the source
                        if (source.has("title")) {
                            recommendations.add(source.path("title").asText());
                        }
                    }
                }

                logger.info("Found {} recommendations for user ID: {}", recommendations.size(), userId);
            } else {
                logger.error("Elasticsearch query failed with status code: {}, response: {}",
                        response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error querying Elasticsearch for user recommendations", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return recommendations;
    }

    /**
     * Get the latest sentiment for a user from user-sentiment index
     *
     * @param userId The Telegram user ID
     * @return An Optional containing the sentiment or empty if not found
     */
    public Optional<String> getLatestUserSentiment(Long userId) {
        try {
            // Modified query to remove the timestamp sort which was causing the error
            String requestBody = "{\n" +
                    "  \"size\": 1,\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"must\": [\n" +
                    "        { \"match\": { \"user_id\": \"" + userId + "\" } }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            String searchUrl = elasticsearchUrl + "/user-sentiment-*/_search";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                JsonNode hits = jsonResponse.path("hits").path("hits");

                if (hits.isArray() && hits.size() > 0) {
                    JsonNode source = hits.get(0).path("_source");
                    if (source.has("sentiment")) {
                        String sentiment = source.path("sentiment").asText();
                        logger.info("Found latest sentiment for user {}: {}", userId, sentiment);
                        return Optional.of(sentiment);
                    }
                }
            } else {
                logger.error("Elasticsearch sentiment query failed: {}", response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error querying Elasticsearch for user sentiment", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return Optional.empty();
    }

    /**
     * Inspect the actual mapping of the Elasticsearch indices
     * This is a diagnostic method to help understand the structure of the indices
     */
    public void inspectIndices() {
        try {
            // List all indices
            String url = elasticsearchUrl + "/_cat/indices?format=json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode indices = objectMapper.readTree(response.body());
                logger.info("Available indices: {}", indices);

                // For each index, get its mapping
                if (indices.isArray()) {
                    for (JsonNode index : indices) {
                        String indexName = index.path("index").asText();
                        if (indexName.startsWith("user-sentiment-") || indexName.startsWith("recommendations-")) {
                            inspectIndexMapping(indexName);
                        }
                    }
                }
            } else {
                logger.error("Failed to list indices: {}", response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error inspecting Elasticsearch indices", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void inspectIndexMapping(String indexName) {
        try {
            String url = elasticsearchUrl + "/" + indexName + "/_mapping";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode mapping = objectMapper.readTree(response.body());
                logger.info("Mapping for index {}: {}", indexName, mapping);
            } else {
                logger.error("Failed to get mapping for index {}: {}", indexName, response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error inspecting index mapping", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Query for a sample document to understand the actual structure
     *
     * @param indexName The index to query
     */
    public void getSampleDocument(String indexName) {
        try {
            String requestBody = "{\n" +
                    "  \"size\": 1,\n" +
                    "  \"query\": {\n" +
                    "    \"match_all\": {}\n" +
                    "  }\n" +
                    "}";

            String searchUrl = elasticsearchUrl + "/" + indexName + "/_search";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                logger.info("Sample document from {}: {}", indexName, jsonResponse);
            } else {
                logger.error("Failed to get sample document from {}: {}", indexName, response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error getting sample document", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}