// src/main/java/org/iss/bigdata/practice/TelegramBotSessionManager.java
package org.iss.bigdata.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession; // Import BotSession
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramBotSessionManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotSessionManager.class);
    private Thread botThread;
    private TelegramBotListener bot;
    private volatile Boolean isStarted = false; // Make volatile for thread safety
    private static final TelegramBotSessionManager INSTANCE = new TelegramBotSessionManager();
    private final Config config;

    private volatile BotSession session; // Store the BotSession instance

    private TelegramBotSessionManager() {
        this.config = Config.loadFromEnvironment();

    }

    public static TelegramBotSessionManager getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (isStarted) {
            logger.warn("Start called but bot session is already started.");
            return;
        }
        isStarted = true; // Set state early
        botThread = Thread.ofVirtual().start(() -> {
            try {
                ProjectKafkaProducer producer = new ProjectKafkaProducer(
                        config.getKafkaBootstrapServers(),
                        config.getSaslUsername(),
                        config.getSaslPassword()
                );
                logger.info("Kafka producer initialized successfully");
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                bot = new TelegramBotListener(
                        config.getTelegramBotToken(),
                        config.getTelegramBotUsername(),
                        config.getKafkaTopic(),
                        producer
                );
                // Store the session instance
                session = botsApi.registerBot(bot);
                logger.info("Telegram bot registered successfully: {}", config.getTelegramBotUsername());
                // Block this thread to keep the bot session alive until stopped or interrupted
                Thread.currentThread().join();
            } catch (TelegramApiException e) {
                logger.error("Telegram API error during bot registration or operation", e);
                isStarted = false; // Reset state on failure
            } catch (InterruptedException e) {
                logger.info("Bot thread interrupted, likely during shutdown.");
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } catch (Exception e) {
                logger.error("Unexpected error in bot thread", e);
                isStarted = false; // Reset state on failure
            } finally {
                logger.info("Bot thread finished.");
                // Ensure state is false if thread exits unexpectedly
                isStarted = false;
                session = null; // Clear session reference
                // Note: bot.close() is called from the main close() method
            }
        });
        // Add a shutdown hook in the main application logic, not here
        // Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    public Boolean getIsStarted() {
        // Check both the flag and if the session object is active
        return isStarted && session != null && session.isRunning();
    }

    @Override
    public void close() {
        if (!isStarted) {
            logger.warn("Close called but bot session is not started or already closing.");
            return;
        }
        logger.info("Closing bot session...");

        // 1. Stop the Telegram bot session first
        BotSession currentSession = this.session; // Local copy for thread safety
        if (currentSession != null && currentSession.isRunning()) {
            logger.info("Stopping Telegram bot session...");
            currentSession.stop(); // This signals the Telegram library to stop polling
            this.session = null; // Clear the session reference
            logger.info("Telegram bot session stopped.");
        } else {
            logger.warn("Telegram bot session was null or not running when close was called.");
        }

        // 2. Close the bot listener (which closes the Kafka producer)
        if (bot != null) {
            try {
                bot.close(); // Closes Kafka producer
                logger.info("Bot listener resources closed.");
            } catch (Exception e) {
                logger.error("Error closing bot listener resources", e);
            }
        } else {
            logger.warn("Bot listener instance was null when close was called.");
        }


        // 3. Interrupt the virtual thread (if it hasn't exited already from session.stop())
        if (botThread != null && botThread.isAlive()) {
            logger.info("Interrupting bot thread...");
            botThread.interrupt();
            try {
                // Optionally wait a short time for the thread to finish
                botThread.join(2000); // Wait up to 2 seconds
                if (botThread.isAlive()) {
                    logger.warn("Bot thread did not terminate after interrupt and join timeout.");
                } else {
                    logger.info("Bot thread terminated successfully.");
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for bot thread to join.");
                Thread.currentThread().interrupt();
            }
        }

        // 4. Update the state
        isStarted = false;
        logger.info("Bot session close process completed.");
    }
}