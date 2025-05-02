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
    private final HttpClient httpClient;
    private final String elasticsearchUrl;
    private final ObjectMapper objectMapper;
    private static final ElasticsearchClient INSTANCE = new ElasticsearchClient();

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
     * @param userId The Telegram user ID to search for
     * @return List of recommended song titles or empty list if none found
     */
    public List<String> getMusicRecommendationsForUser(Long userId) {
        List<String> recommendations = new ArrayList<>();
        
        try {
            // Query the recommendations index for this user's latest recommendations
            String requestBody = "{\n" +
                    "  \"size\": 1,\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"must\": [\n" +
                    "        { \"match\": { \"user_id\": \"" + userId + "\" } }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"sort\": [\n" +
                    "    { \"timestamp\": { \"order\": \"desc\" } }\n" +
                    "  ]\n" +
                    "}";

            // We want to search across all days
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
                
                // Extract song titles from the response
                if (hits.isArray() && hits.size() > 0) {
                    for (JsonNode hit : hits) {
                        JsonNode source = hit.path("_source");
                        // Extract song recommendations from the source
                        if (source.has("recommendations")) {
                            JsonNode recommendations_node = source.path("recommendations");
                            if (recommendations_node.isArray()) {
                                for (JsonNode rec : recommendations_node) {
                                    if (rec.has("title")) {
                                        recommendations.add(rec.path("title").asText());
                                    }
                                }
                            }
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
     * @param userId The Telegram user ID
     * @return An Optional containing the sentiment or empty if not found
     */
    public Optional<String> getLatestUserSentiment(Long userId) {
        try {
            String requestBody = "{\n" +
                    "  \"size\": 1,\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"must\": [\n" +
                    "        { \"match\": { \"user_id\": \"" + userId + "\" } }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"sort\": [\n" +
                    "    { \"timestamp\": { \"order\": \"desc\" } }\n" +
                    "  ]\n" +
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
}