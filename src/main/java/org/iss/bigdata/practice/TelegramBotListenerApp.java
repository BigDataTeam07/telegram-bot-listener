package org.iss.bigdata.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
// src/main/java/org/iss/bigdata/practice/TelegramBotListenerApp.java
public class TelegramBotListenerApp {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotListenerApp.class);

    public static void main(String[] args) {
        logger.info("Starting Telegram Kafka Producer Application");

        // singleton instances
        HTTPEndpointListener httpEndpointListener = HTTPEndpointListener.getInstance();
        TelegramBotSessionManager botSessionManager = TelegramBotSessionManager.getInstance();
        // start the bot session
        botSessionManager.start();
        // shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(botSessionManager::close));
        // start the HTTP endpoint listener
        httpEndpointListener.start();

    }
}