package org.iss.bigdata.practice.clients;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPEndpointClient {
    private static final Logger logger = LoggerFactory.getLogger(HTTPEndpointClient.class);
    private static final int PORT = 8080;
    private static final String SHUTDOWN_PATH = "/shutdown";
    private static final String STARTUP_PATH = "/start";
    private final TelegramBotSessionManager botSession;
    private static final HTTPEndpointClient INSTANCE = new HTTPEndpointClient();

    private HTTPEndpointClient() {
        this.botSession = TelegramBotSessionManager.getInstance();
    }

    public static HTTPEndpointClient getInstance() {
        return INSTANCE;
    }

    public void start() {
        // blocking the main thread
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext(SHUTDOWN_PATH, exchange -> {
                String response;
                if (!botSession.getIsStarted()) {
                    response = "Bot is already stopped";
                    logger.info("Shutdown requested but bot is already stopped");
                } else {
                    botSession.close();
                    response = "Bot stopped";
                    logger.info("Bot stopped via HTTP shutdown");
                }
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.createContext(STARTUP_PATH, exchange -> {
                String response;
                if (botSession.getIsStarted()) {
                    response = "Bot is already started";
                    logger.info("Startup requested but bot is already running");
                } else {
                    botSession.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(botSession::close));
                    response = "Bot started";
                    logger.info("Bot started via HTTP start");
                }
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.setExecutor(null);
            server.start();
            logger.info("HTTP control server started on port {}", PORT);
        } catch (IOException e) {
            logger.error("Failed to start HTTP control server", e);
        }
    }
}