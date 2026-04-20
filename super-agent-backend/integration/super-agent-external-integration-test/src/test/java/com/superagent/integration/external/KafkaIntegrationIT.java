package com.superagent.integration.external;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaIntegrationIT extends AbstractExternalIntegrationIT {

    @Test
    void shouldProduceAndConsumeKafkaMessage() throws Exception {
        ExternalServiceIntegrationProperties.KafkaProperties kafka = properties.getKafka();
        ensureTopicExists(kafka.getBootstrapServers(), kafka.getTopic());

        String key = runId("kafka-key");
        String value = runId("kafka-value");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties(kafka.getBootstrapServers()));
             KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties(kafka.getBootstrapServers()))) {
            consumer.subscribe(List.of(kafka.getTopic()));
            consumer.poll(Duration.ofSeconds(1));

            producer.send(new ProducerRecord<>(kafka.getTopic(), key, value)).get();
            producer.flush();

            ConsumerRecord<String, String> record = waitForRecord(consumer, key, value);
            assertThat(record.topic()).isEqualTo(kafka.getTopic());
            assertThat(record.key()).isEqualTo(key);
            assertThat(record.value()).isEqualTo(value);
        }
    }

    private void ensureTopicExists(String bootstrapServers, String topicName) throws Exception {
        try (AdminClient adminClient = AdminClient.create(Map.of("bootstrap.servers", bootstrapServers))) {
            if (!adminClient.listTopics().names().get().contains(topicName)) {
                adminClient.createTopics(List.of(new NewTopic(topicName, 1, (short) 1))).all().get();
            }
        }
    }

    private Properties producerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return properties;
    }

    private Properties consumerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, runId("integration-kafka-consumer"));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50");
        return properties;
    }

    private ConsumerRecord<String, String> waitForRecord(
            KafkaConsumer<String, String> consumer,
            String key,
            String value) {
        long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))
                    .records(properties.getKafka().getTopic())) {
                if (key.equals(record.key()) && value.equals(record.value())) {
                    return record;
                }
            }
        }
        throw new IllegalStateException("Kafka message was not consumed within timeout.");
    }
}
