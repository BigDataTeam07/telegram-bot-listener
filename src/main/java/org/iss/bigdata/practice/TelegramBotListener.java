package org.iss.bigdata.practice;

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

    public TelegramBotListener(String botToken, String botUsername
                               , String kafkaTopic, ProjectKafkaProducer projectKafkaProducer) {
        super(botToken);
        this.botUsername = botUsername;
        this.kafkaTopic = kafkaTopic;
        this.objectMapper = new ObjectMapper();

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
            // For channel posts, include channel information

            if (!update.hasMessage() || !update.getMessage().hasText()) {
                logger.warn("Update does not contain a text message");
                return;
            }
            Message message = update.getMessage();
            String messageText = message.getText();
            // skip if the message is from a bot
            if (message.getFrom().getIsBot()) {
                logger.info("Ignoring message from bot: {}", update.getMessage().getFrom().getUserName());
                return;
            }
            // skip if message is not from group
            if (message.getChat().isUserChat()) {
                logger.info("Ignoring message from user chat: {}", update.getMessage().getFrom().getUserName());
                return;
            }

            // Check if the message starts with the bot's username mention
            if (messageText.trim().toLowerCase().startsWith("@" + this.botUsername.toLowerCase())) {
                logger.info("Received mention from user {}: {}", message.getFrom().getUserName(), messageText);
                sendReply(message.getChatId(), "Hello! How can I assist you?");
                return; // Stop processing further if it's a mention/command
            }

            // Process the message

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

        } catch (Exception e) {
            logger.error("Error processing update", e);
        }
    }

    private String getTelegramJsonMessage(Message message, String messageText) throws JsonProcessingException {
        // Convert to JSON
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.put("user_id", message.getFrom().getId());
        String userName = message.getFrom().getUserName() != null ? message.getFrom().getUserName() : "unknown";
        jsonNode.put("username", userName);
        jsonNode.put("message", messageText);
        jsonNode.put("chat_id", message.getChatId());
        jsonNode.put("chat_name", message.getChat().getTitle());
        jsonNode.put("timestamp", Instant.now().toEpochMilli());

        String jsonMessage = objectMapper.writeValueAsString(jsonNode);
        return jsonMessage;
    }

    private void sendReply(Long chatId, String replyText) {
        SendMessage replyMessage = new SendMessage();
        replyMessage.setChatId(chatId.toString()); // Set the chat ID to reply to
        replyMessage.setText(replyText); // Set the reply text

        try {
            execute(replyMessage); // Send the message
            logger.info("Sent 'hello' reply to chat ID: {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Failed to send 'hello' reply to chat ID: {}", chatId, e);
        }
    }

    @Override
    public void close() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
            logger.info("Kafka producer closed");
        }
        // close the connection, stop polling
    }
}