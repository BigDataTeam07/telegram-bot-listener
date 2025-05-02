package org.iss.bigdata.practice.service;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class ProjectKafkaProducer {
    private final KafkaProducer projectKafkaProducer;
    // Configure Kafka producer
    public ProjectKafkaProducer(String bootstrapServers, String saslUsername, String saslPassword) {
        // Configure Kafka producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "SCRAM-SHA-512");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                        "username=" + saslUsername + " password=\"" + saslPassword + "\";");
        this.projectKafkaProducer = new KafkaProducer<>(props);

    }


    public KafkaProducer getProjectKafkaProducer() {
        return projectKafkaProducer;
    }

    public void close() {
        if (projectKafkaProducer != null) {
            projectKafkaProducer.close();
        }
    }

}
