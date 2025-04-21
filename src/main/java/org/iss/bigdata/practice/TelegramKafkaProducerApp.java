package org.iss.bigdata.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramKafkaProducerApp {
    private static final Logger logger = LoggerFactory.getLogger(TelegramKafkaProducerApp.class);

    public static void main(String[] args) {
        logger.info("Starting Telegram Kafka Producer Application");

        // Load configuration
        Config config = Config.loadFromEnvironment();
        logger.info("Configuration loaded successfully");

        try {
            // Initialize Telegram Bot
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Create and register the bot
            TelegramBotListener bot = new TelegramBotListener(
                    config.getTelegramBotToken(),
                    config.getTelegramBotUsername(),
                    config.getKafkaBootstrapServers(),
                    config.getKafkaTopic()
            );

            botsApi.registerBot(bot);
            logger.info("Telegram bot registered successfully: {}", config.getTelegramBotUsername());

            // Keep the application running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down application...");
                bot.close();
            }));

            // Block main thread to keep application running
            Thread.currentThread().join();

        } catch (TelegramApiException e) {
            logger.error("Failed to register Telegram bot", e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.info("Application interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}