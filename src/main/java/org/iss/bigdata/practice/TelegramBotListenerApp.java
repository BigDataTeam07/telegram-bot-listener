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
        HTTPEndpointListener httpEndpointListener = HTTPEndpointListener.getInstance();
        TelegramBotSessionManager botSessionManager = TelegramBotSessionManager.getInstance();
        botSessionManager.start();
        httpEndpointListener.start();


    }
}