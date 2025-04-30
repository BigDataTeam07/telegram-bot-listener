package org.iss.bigdata.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

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
        String saslUsername = getRequiredEnv("KAFKA_SASL_USERNAME");
        String saslPassword = getRequiredEnv("KAFKA_SASL_PASSWORD");
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



    // If you need more information about configurations or implementing the sample
    // code, visit the AWS docs:
    // https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html

    public static String[] getSecret() {

        String secretName = "music-review-msk-kafka-client";
        Region region = Region.of("ap-southeast-1");

        // Create a Secrets Manager client
        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .build();

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse getSecretValueResponse;

        try {
            getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
        } catch (Exception e) {
            // For a list of exceptions thrown, see
            // https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
            throw e;
        }

        String secret = getSecretValueResponse.secretString();

        // decode username and password from the secret
        String[] parts = secret.split(":", 2);
        if ( parts.length != 2 ) {
            throw new IllegalArgumentException("Secret format is invalid");
        }
        String username = parts[0];
        String password = parts[1];
        return new String[]{username, password};
    }
}