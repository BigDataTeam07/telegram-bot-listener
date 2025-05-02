package org.iss.bigdata.practice.service;

import org.iss.bigdata.practice.clients.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Service to handle music recommendation logic
 */
public class MusicRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(MusicRecommendationService.class);
    private static final MusicRecommendationService INSTANCE = new MusicRecommendationService();
    // Fallback recommendations when Elasticsearch doesn't return any results
    private static final List<String> FALLBACK_GENERAL_RECOMMENDATIONS = Arrays.asList(
            "Bohemian Rhapsody by Queen",
            "Billie Jean by Michael Jackson",
            "Hey Jude by The Beatles",
            "Imagine by John Lennon",
            "Shape of You by Ed Sheeran",
            "Despacito by Luis Fonsi ft. Daddy Yankee",
            "Rolling in the Deep by Adele",
            "Smells Like Teen Spirit by Nirvana"
    );
    private final ElasticsearchClient elasticsearchClient;
    private final Random random = new Random();

    private MusicRecommendationService() {
        this.elasticsearchClient = ElasticsearchClient.getInstance();
    }

    public static MusicRecommendationService getInstance() {
        return INSTANCE;
    }

    /**
     * Get recommendation message for a user
     *
     * @param userId   The user's Telegram ID
     * @param username The user's Telegram username
     * @return A message with recommendations or appropriate fallback message
     */
    public String getRecommendationMessage(Long userId, String username) {
        logger.info("Getting music recommendations for user: {} (ID: {})", username, userId);

        try {

            // Get music recommendations
            HashMap<String, String> recommendations = elasticsearchClient.getMusicRecommendationsForUser(userId);


            if (recommendations == null || recommendations.isEmpty()) {
                return generateGenericFallbackMessage(username);
            } else {
                return formatRecommendations(username, recommendations);
            }
        } catch (Exception e) {
            logger.error("Error while getting recommendations", e);
            return generateGenericFallbackMessage(username);
        }
    }

    /**
     * Format recommendations in a user-friendly way
     */
    private String formatRecommendations(String username, HashMap<String, String> recommendations) {
        StringBuilder messageBuilder = new StringBuilder();


        messageBuilder.append(String.format("Here are some music recommendations for you, @%s:\n\n", username));

        // telegram bot markdown v2 format
        // title - link to the song, link format: https://www.amazon.com/dp/${productId}
        int count = 0;
        for (String productId : recommendations.keySet()) {
            count++;
            String musicTitle = recommendations.get(productId);
            String amazonLink = String.format("https://www.amazon.com/dp/%s", productId);
            messageBuilder.append(String.format("%d. %s [view on amazon](%s)\n", count, musicTitle, amazonLink));
        }

        messageBuilder.append("\nEnjoy listening! 🎵");
        return messageBuilder.toString();
    }


    /**
     * Get a random sublist of recommendations
     */
    private List<String> getRandomSublist(List<String> source, int count) {
        // Ensure we don't try to get more items than available
        count = Math.min(count, source.size());

        // Get a random starting point
        int start = random.nextInt(source.size() - count + 1);
        return source.subList(start, start + count);
    }

    /**
     * Generate a completely generic fallback message when everything fails
     */
    private String generateGenericFallbackMessage(String username) {
        List<String> recommendations = getRandomSublist(FALLBACK_GENERAL_RECOMMENDATIONS, 3);

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(String.format("Hi @%s! Your data is not available in our database. But don't worry!\n" +
                ", here are %s general popular songs for you:\n\n", username, recommendations.size()));

        for (int i = 0; i < recommendations.size(); i++) {
            messageBuilder.append(String.format("%d. %s\n", i + 1, recommendations.get(i)));
        }

        messageBuilder.append("\nEnjoy listening! 🎵");
        return messageBuilder.toString();
    }
}