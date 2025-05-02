package org.iss.bigdata.practice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;


public class TelegramBotListener extends TelegramLongPollingBot implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotListener.class);
    private final String botUsername;
    private final Producer<String, String> kafkaProducer;
    private final String kafkaTopic;
    private final ObjectMapper objectMapper;
    private final MusicRecommendationService recommendationService;

    // Command constant
    private final String MUSIC_RECOMMEND_COMMAND;

    public TelegramBotListener(String botToken, String botUsername,
                               String kafkaTopic, ProjectKafkaProducer projectKafkaProducer) {
        super(botToken);
        this.botUsername = botUsername;
        this.MUSIC_RECOMMEND_COMMAND = "@" + botUsername;
        this.kafkaTopic = kafkaTopic;
        this.objectMapper = new ObjectMapper();
        this.recommendationService = MusicRecommendationService.getInstance();
        this.kafkaProducer = projectKafkaProducer.getProjectKafkaProducer();
        logger.info("Kafka producer initialized successfully");
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // Check if the update has a message and the message has text
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                logger.warn("Update does not contain a text message");
                return;
            }

            Message message = update.getMessage();
            String messageText = message.getText().trim();

            // Skip if the message is from a bot
            if (message.getFrom().getIsBot()) {
                logger.info("Ignoring message from bot: {}", message.getFrom().getUserName());
                return;
            }

            // Skip if message is not from group (optional, remove if you want private chat support)
            if (message.getChat().isUserChat()) {
                logger.info("Ignoring message from user chat: {}", message.getFrom().getUserName());
                handlePrivateChat(message);
                return;
            }

            // Check for music recommendation command anywhere in the message
            if (messageText.toLowerCase().contains(MUSIC_RECOMMEND_COMMAND.toLowerCase())) {
                logger.info("Music recommendation command detected from user: {}", message.getFrom().getUserName());
                handleMusicRecommendation(message);
                // We still want to send this message to Kafka for sentiment analysis
                sendMessageToKafka(message);
                return;
            }


            // Process regular messages for sentiment analysis via Kafka
            sendMessageToKafka(message);

        } catch (Exception e) {
            logger.error("Error processing update", e);
        }
    }

    /**
     * Handle private chat messages if allowed
     */
    private void handlePrivateChat(Message message) {
        return;
    }

    /**
     * Handle the music recommendation command
     */
    private void handleMusicRecommendation(Message message) {
        Long userId = message.getFrom().getId();
        String username = message.getFrom().getUserName() != null ?
                message.getFrom().getUserName() : "user";

        logger.info("Processing music recommendation command for user: {} (ID: {})", username, userId);

        // Get recommendations from our service
        String recommendationMessage = recommendationService.getRecommendationMessage(userId, username);

        // Send the recommendation message to the chat
        sendReply(message.getChatId(), recommendationMessage);
    }

    /**
     * Send a message to Kafka for sentiment analysis
     */
    private void sendMessageToKafka(Message message) throws JsonProcessingException {
        String messageText = message.getText();
        String jsonMessage = getTelegramJsonMessage(message, messageText);

        // Send to Kafka
        String key = String.valueOf(message.getFrom().getId());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(kafkaTopic, key, jsonMessage);

        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Error sending message to Kafka", exception);
            } else {
                logger.info("Message sent to topic: {}, partition: {}, offset: {}",
                        metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    private String getTelegramJsonMessage(Message message, String messageText) throws JsonProcessingException {
        // Convert to JSON
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.put("user_id", message.getFrom().getId());
        String userName = message.getFrom().getUserName() != null ? message.getFrom().getUserName() : "unknown";
        jsonNode.put("username", userName);
        jsonNode.put("message", messageText);
        jsonNode.put("chat_id", message.getChatId());
        String chatTitle = message.getChat().getTitle();
        jsonNode.put("chat_name", chatTitle != null ? chatTitle : "Private Chat");
        jsonNode.put("timestamp", Instant.now().toEpochMilli());

        return objectMapper.writeValueAsString(jsonNode);
    }

    private void sendReply(Long chatId, String replyText) {
        SendMessage replyMessage = new SendMessage();
        replyMessage.setChatId(chatId.toString());
        replyMessage.setText(replyText);
        replyMessage.enableMarkdown(true); // Enable Markdown for better formatting

        try {
            execute(replyMessage);
            logger.info("Sent reply to chat ID: {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Failed to send reply to chat ID: {}", chatId, e);

            // Try again without markdown if it fails
            try {
                replyMessage.enableMarkdown(false);
                execute(replyMessage);
                logger.info("Sent plain text reply to chat ID: {}", chatId);
            } catch (TelegramApiException e2) {
                logger.error("Failed to send plain text reply to chat ID: {}", chatId, e2);
            }
        }
    }

    @Override
    public void close() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
            logger.info("Kafka producer closed");
        }
    }
}