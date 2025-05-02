package org.iss.bigdata.practice.service;

import org.iss.bigdata.practice.clients.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service to handle music recommendation logic
 */
public class MusicRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(MusicRecommendationService.class);
    private final ElasticsearchClient elasticsearchClient;
    private static final MusicRecommendationService INSTANCE = new MusicRecommendationService();

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
        
        // Get the latest sentiment for context
        Optional<String> latestSentiment = elasticsearchClient.getLatestUserSentiment(userId);
        
        // Get music recommendations
        List<String> recommendations = elasticsearchClient.getMusicRecommendationsForUser(userId);
        
        // Build appropriate response
        if (recommendations.isEmpty()) {
            // No recommendations found
            if (latestSentiment.isPresent()) {
                return String.format("I don't have specific music recommendations for you yet, @%s. " +
                        "Your latest mood seems to be %s. Keep chatting and I'll generate recommendations for you soon!",
                        username, latestSentiment.get());
            } else {
                return String.format("I don't have any music recommendations for you yet, @%s. " +
                        "Keep chatting in the group and I'll analyze your messages to suggest music you might like!",
                        username);
            }
        } else {
            // Format recommendations nicely
            StringBuilder messageBuilder = new StringBuilder();
            
            if (latestSentiment.isPresent()) {
                messageBuilder.append(String.format("Based on your %s mood, @%s, here are some music recommendations for you:\n\n",
                        latestSentiment.get(), username));
            } else {
                messageBuilder.append(String.format("Here are some music recommendations for you, @%s:\n\n", username));
            }
            
            for (int i = 0; i < recommendations.size(); i++) {
                messageBuilder.append(String.format("%d. %s\n", i + 1, recommendations.get(i)));
            }
            
            messageBuilder.append("\nEnjoy listening! 🎵");
            return messageBuilder.toString();
        }
    }
}