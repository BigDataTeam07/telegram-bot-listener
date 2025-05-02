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
import java.util.HashSet;
import java.util.List;

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
        HashSet<String> recommendations = new HashSet<String>();

        try {
            // Query with time range filter for the last 24 hours
            String requestBody = "{\n" +
                    "  \"size\": 3,\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"must\": [\n" +
                    "        { \"term\": { \"userId.keyword\": \"" + userId + "\" } },\n" +
                    "        { \"range\": { \"@timestamp\": { \"gte\": \"now-24h/h\", \"lte\": \"now\" } } }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"sort\": [\n" +
                    "    { \"@timestamp\": { \"order\": \"desc\" } }\n" +
                    "  ]\n" +
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

                logger.info("Found {} recommendations from the last 24 hours for user ID: {}",
                        recommendations.size(), userId);
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

        return recommendations.stream().toList();
    }

}