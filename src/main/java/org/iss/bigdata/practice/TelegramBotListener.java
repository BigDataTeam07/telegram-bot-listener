package org.iss.bigdata.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class TelegramBotListener extends TelegramLongPollingBot implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotListener.class);
    private final String botUsername;
    private final Producer<String, String> kafkaProducer;
    private final String kafkaTopic;
    private final ObjectMapper objectMapper;

    public TelegramBotListener(String botToken, String botUsername,
                               String bootstrapServers, String kafkaTopic, String saslUsername, String saslPassword) {
        super(botToken);
        this.botUsername = botUsername;
        this.kafkaTopic = kafkaTopic;
        this.objectMapper = new ObjectMapper();

        // Configure Kafka producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                        "username=\"user1\" " +
                        "password=\"user1-password\";");

        this.kafkaProducer = new KafkaProducer<>(props);
        logger.info("Kafka producer initialized with bootstrap servers: {}", bootstrapServers);
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
            if (update.hasChannelPost() ) {
                // skip channel posts
                return;
            }
            if (!update.hasMessage() || !update.getMessage().hasText() ) {
                logger.warn("Update does not contain a text message");
                return;
            }
            if (update.getMessage().getFrom() != null) {
                Message message = update.getMessage();

                String messageText = message.getText();

                logger.info("Received message from user {}: {}",
                        message.getFrom().getUserName(), messageText);

                // Convert to JSON
                ObjectNode jsonNode = objectMapper.createObjectNode();
                jsonNode.put("user_id", message.getFrom().getId());
                jsonNode.put("username", message.getFrom().getUserName());
                jsonNode.put("message", messageText);
                jsonNode.put("chat_id", message.getChatId());
                jsonNode.put("chat_name", message.getChat().getTitle());
                jsonNode.put("timestamp", Instant.now().toEpochMilli());


                String jsonMessage = objectMapper.writeValueAsString(jsonNode);

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
        } catch (Exception e) {
            logger.error("Error processing update", e);
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