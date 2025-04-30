// src/main/java/org/iss/bigdata/practice/TelegramBotSessionManager.java
package org.iss.bigdata.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramBotSessionManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotSessionManager.class);
    private Thread botThread;
    private TelegramBotListener bot;
    private Boolean isStarted = false;
    private static final TelegramBotSessionManager INSTANCE = new TelegramBotSessionManager();
    private final Config config;
    private final ProjectKafkaProducer producer;

    private TelegramBotsApi botsApi;

    private TelegramBotSessionManager() {
        this.config = Config.loadFromEnvironment();
        this.producer = new ProjectKafkaProducer(
                config.getKafkaBootstrapServers(),
                config.getSaslUsername(),
                config.getSaslPassword()
        );
        logger.info("Kafka producer initialized successfully");
    }
    public static TelegramBotSessionManager getInstance() {
        return INSTANCE;
    }
    public void start() {
        if (isStarted) {
            return;
        }
        isStarted = true;
        botThread = Thread.ofVirtual().start(() -> {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                bot = new TelegramBotListener(
                        config.getTelegramBotToken(),
                        config.getTelegramBotUsername(),
                        config.getKafkaTopic(),
                        producer
                );
                botsApi.registerBot(bot);
                logger.info("Telegram bot registered successfully: {}", config.getTelegramBotUsername());
                // Block this thread to keep the bot session alive
                Thread.currentThread().join();
            } catch (TelegramApiException e) {
                // Handle specific API exceptions if needed
                logger.error("Telegram API error during bot registration or operation", e);
            } catch (InterruptedException e) {
                // This is expected when botThread.interrupt() is called during close()
                logger.info("Bot thread interrupted shutdown.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Catch other potential runtime exceptions
                logger.error("Unexpected error in bot session thread", e);
            } finally {
                logger.info("Bot session thread exiting");
                isStarted = false;
                bot = null;
                botsApi = null;
            }
        });
    }

    public Boolean getIsStarted() {
        return isStarted && bot != null && botThread != null && botThread.isAlive();
    }

    @Override
    public void close() {
        if (bot != null) {
            bot.close();
        }
        if (botThread != null) {
            botThread.interrupt();
        }
        isStarted = false;
        logger.info("Bot session closed");
    }
}