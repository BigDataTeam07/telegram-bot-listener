package org.iss.bigdata.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private final String telegramBotToken;
    private final String telegramBotUsername;
    private final String kafkaBootstrapServers;
    private final String kafkaTopic;
    private final String saslUsername;
    private final String saslPassword;
    // Constructor
    public Config(String telegramBotToken, String telegramBotUsername,
                  String kafkaBootstrapServers, String kafkaTopic, String saslUsername, String saslPassword) {
        this.telegramBotToken = telegramBotToken;
        this.telegramBotUsername = telegramBotUsername;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.kafkaTopic = kafkaTopic;
        this.saslUsername = saslUsername;
        this.saslPassword = saslPassword;
    }

    // Load configuration from environment variables with defaults
    public static Config loadFromEnvironment() {
        String telegramBotToken = getRequiredEnv("TELEGRAM_BOT_TOKEN");
        String telegramBotUsername = getRequiredEnv("TELEGRAM_BOT_USERNAME");
        String kafkaBootstrapServers = getEnv("KAFKA_BOOTSTRAP_SERVERS",
                "localhost:9092");
        String kafkaTopic = getEnv("KAFKA_TOPIC", "social-media-topic");
        String saslUsername = getEnv("KAFKA_SASL_USERNAME", "user1");
        String saslPassword = getEnv("KAFKA_SASL_PASSWORD", "user1-password");
        return new Config(telegramBotToken, telegramBotUsername, kafkaBootstrapServers, kafkaTopic, saslUsername, saslPassword);
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            logger.error("Required environment variable '{}' is not set", name);
            throw new IllegalStateException("Required environment variable '" + name + "' is not set");
        }
        return value;
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            logger.info("Environment variable '{}' not set, using default: {}", name, defaultValue);
            return defaultValue;
        }
        return value;
    }

    // Getters
    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public String getTelegramBotUsername() {
        return telegramBotUsername;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public String getSaslUsername() {
        return saslUsername;
    }

    public String getSaslPassword() {
        return saslPassword;
    }
}