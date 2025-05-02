package org.iss.bigdata.practice.service;

import org.iss.bigdata.practice.clients.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Service to handle music recommendation logic
 */
public class MusicRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(MusicRecommendationService.class);
    private final ElasticsearchClient elasticsearchClient;
    private static final MusicRecommendationService INSTANCE = new MusicRecommendationService();

    // Fallback recommendations when Elasticsearch doesn't return any results
    private static final List<String> FALLBACK_HAPPY_RECOMMENDATIONS = Arrays.asList(
            "Happy by Pharrell Williams",
            "Uptown Funk by Mark Ronson ft. Bruno Mars",
            "Walking on Sunshine by Katrina and The Waves",
            "Good Feeling by Flo Rida",
            "Can't Stop the Feeling by Justin Timberlake"
    );

    private static final List<String> FALLBACK_CALM_RECOMMENDATIONS = Arrays.asList(
            "Weightless by Marconi Union",
            "Clair de Lune by Claude Debussy",
            "Someone Like You by Adele",
            "River Flows in You by Yiruma",
            "Moon River by Henry Mancini"
    );

    private static final List<String> FALLBACK_ENERGETIC_RECOMMENDATIONS = Arrays.asList(
            "Eye of the Tiger by Survivor",
            "Till I Collapse by Eminem",
            "Stronger by Kanye West",
            "Seven Nation Army by The White Stripes",
            "Thunderstruck by AC/DC"
    );

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

    private final Random random = new Random();

    private MusicRecommendationService() {
        this.elasticsearchClient = ElasticsearchClient.getInstance();
    }

    public static MusicRecommendationService getInstance() {
        return INSTANCE;
    }

    /**
     * Get recommendation message for a user
     * @param userId The user's Telegram ID
     * @param username The user's Telegram username
     * @return A message with recommendations or appropriate fallback message
     */
    public String getRecommendationMessage(Long userId, String username) {
        logger.info("Getting music recommendations for user: {} (ID: {})", username, userId);

        try {
            // Get the latest sentiment for context
            Optional<String> latestSentiment = elasticsearchClient.getLatestUserSentiment(userId);

            // Get music recommendations
            List<String> recommendations = elasticsearchClient.getMusicRecommendationsForUser(userId);

            // Build appropriate response
            if (recommendations == null || recommendations.isEmpty()) {
                return generateFallbackRecommendations(username, latestSentiment);
            } else {
                return formatRecommendations(username, latestSentiment, recommendations);
            }
        } catch (Exception e) {
            logger.error("Error while getting recommendations", e);
            return generateGenericFallbackMessage(username);
        }
    }

    /**
     * Format recommendations in a user-friendly way
     */
    private String formatRecommendations(String username, Optional<String> sentiment, List<String> recommendations) {
        StringBuilder messageBuilder = new StringBuilder();

        if (sentiment.isPresent()) {
            messageBuilder.append(String.format("Based on your %s mood, @%s, here are some music recommendations for you:\n\n",
                    sentiment.get(), username));
        } else {
            messageBuilder.append(String.format("Here are some music recommendations for you, @%s:\n\n", username));
        }

        for (int i = 0; i < recommendations.size(); i++) {
            messageBuilder.append(String.format("%d. %s\n", i + 1, recommendations.get(i)));
        }

        messageBuilder.append("\nEnjoy listening! 🎵");
        return messageBuilder.toString();
    }

    /**
     * Generate fallback recommendations when we have sentiment but no actual recommendations
     */
    private String generateFallbackRecommendations(String username, Optional<String> sentiment) {
        List<String> recommendations;

        if (sentiment.isPresent()) {
            String mood = sentiment.get().toLowerCase();

            // Select recommendations based on the sentiment
            if (mood.contains("happy") || mood.contains("positive") || mood.contains("joyful")) {
                recommendations = getRandomSublist(FALLBACK_HAPPY_RECOMMENDATIONS, 3);
            } else if (mood.contains("calm") || mood.contains("peaceful") || mood.contains("relaxed")) {
                recommendations = getRandomSublist(FALLBACK_CALM_RECOMMENDATIONS, 3);
            } else if (mood.contains("energetic") || mood.contains("excited") || mood.contains("motivated")) {
                recommendations = getRandomSublist(FALLBACK_ENERGETIC_RECOMMENDATIONS, 3);
            } else {
                recommendations = getRandomSublist(FALLBACK_GENERAL_RECOMMENDATIONS, 3);
            }

            return formatRecommendations(username, sentiment, recommendations);
        } else {
            // No sentiment available, provide generic fallback
            recommendations = getRandomSublist(FALLBACK_GENERAL_RECOMMENDATIONS, 3);
            return formatRecommendations(username, Optional.empty(), recommendations);
        }
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
        messageBuilder.append(String.format("Hi @%s! Here are some popular music recommendations for you:\n\n", username));

        for (int i = 0; i < recommendations.size(); i++) {
            messageBuilder.append(String.format("%d. %s\n", i + 1, recommendations.get(i)));
        }

        messageBuilder.append("\nEnjoy listening! 🎵");
        return messageBuilder.toString();
    }
}