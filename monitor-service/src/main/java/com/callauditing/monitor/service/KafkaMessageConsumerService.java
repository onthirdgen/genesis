package com.callauditing.monitor.service;

import com.callauditing.monitor.model.KafkaMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
@Slf4j
public class KafkaMessageConsumerService {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.poll-timeout:30000}")
    private long defaultPollTimeout;

    /**
     * Consume messages from a Kafka topic starting from the beginning
     *
     * @param topic   The topic to consume from
     * @param limit   Maximum number of messages to retrieve
     * @param timeout Timeout in milliseconds
     * @return List of KafkaMessage objects with full metadata
     */
    public List<KafkaMessage> consumeFromBeginning(String topic, int limit, long timeout) {
        return consumeMessages(topic, null, limit, timeout);
    }

    /**
     * Consume messages from a Kafka topic starting from a specific offset
     *
     * @param topic   The topic to consume from
     * @param offset  The offset to start from
     * @param limit   Maximum number of messages to retrieve
     * @param timeout Timeout in milliseconds
     * @return List of KafkaMessage objects with full metadata
     */
    public List<KafkaMessage> consumeFromOffset(String topic, long offset, int limit, long timeout) {
        return consumeMessages(topic, offset, limit, timeout);
    }

    /**
     * Get the latest N messages from a topic (sorted from newest to oldest)
     *
     * @param topic   The topic to consume from
     * @param limit   Maximum number of messages to retrieve
     * @param timeout Timeout in milliseconds
     * @return List of KafkaMessage objects sorted by timestamp descending
     */
    public List<KafkaMessage> getLatestMessages(String topic, int limit, long timeout) {
        List<KafkaMessage> allMessages = new ArrayList<>();
        KafkaConsumer<String, String> consumer = null;

        try {
            consumer = createConsumer();

            // Get partitions for the topic
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(partitionInfo -> new TopicPartition(topic, partitionInfo.partition()))
                    .toList();

            if (partitions.isEmpty()) {
                log.warn("Topic {} has no partitions", topic);
                return allMessages;
            }

            // Assign partitions to consumer
            consumer.assign(partitions);

            // Seek to end for each partition, then go back
            consumer.seekToEnd(partitions);

            // For each partition, go back and read the last messages
            for (TopicPartition partition : partitions) {
                long endOffset = consumer.position(partition);
                long startOffset = Math.max(0, endOffset - limit);
                consumer.seek(partition, startOffset);
            }

            // Poll messages
            long startTime = System.currentTimeMillis();
            long endTime = startTime + timeout;

            while (System.currentTimeMillis() < endTime) {
                long remainingTime = endTime - System.currentTimeMillis();
                if (remainingTime <= 0) {
                    break;
                }

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(Math.min(1000, remainingTime)));

                for (ConsumerRecord<String, String> record : records) {
                    KafkaMessage message = KafkaMessage.builder()
                            .offset(record.offset())
                            .partition(record.partition())
                            .timestamp(Instant.ofEpochMilli(record.timestamp())
                                    .atZone(ZoneOffset.UTC)
                                    .format(DateTimeFormatter.ISO_INSTANT))
                            .key(record.key())
                            .value(record.value())
                            .topic(record.topic())
                            .build();

                    allMessages.add(message);
                }

                if (records.isEmpty()) {
                    break;
                }
            }

            // Sort by timestamp descending (newest first) and limit
            allMessages.sort((m1, m2) -> {
                Instant t1 = Instant.parse(m1.getTimestamp());
                Instant t2 = Instant.parse(m2.getTimestamp());
                return t2.compareTo(t1);
            });
            if (allMessages.size() > limit) {
                allMessages = allMessages.subList(0, limit);
            }

            log.info("Successfully retrieved {} latest messages from topic {}", allMessages.size(), topic);

        } catch (Exception e) {
            log.error("Error getting latest messages from topic {}", topic, e);
            throw new RuntimeException("Failed to get latest messages from topic: " + topic, e);
        } finally {
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    log.error("Error closing consumer", e);
                }
            }
        }

        return allMessages;
    }

    /**
     * Core method to consume messages from Kafka
     */
    private List<KafkaMessage> consumeMessages(String topic, Long startOffset, int limit, long timeout) {
        List<KafkaMessage> messages = new ArrayList<>();
        KafkaConsumer<String, String> consumer = null;

        try {
            // Create a unique consumer group to avoid conflicts
            consumer = createConsumer();

            // Get partitions for the topic
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(partitionInfo -> new TopicPartition(topic, partitionInfo.partition()))
                    .toList();

            if (partitions.isEmpty()) {
                log.warn("Topic {} has no partitions", topic);
                return messages;
            }

            // Assign partitions to consumer
            consumer.assign(partitions);

            // Seek to beginning or specific offset for each partition
            if (startOffset == null) {
                consumer.seekToBeginning(partitions);
                log.info("Seeking to beginning of topic {}", topic);
            } else {
                for (TopicPartition partition : partitions) {
                    consumer.seek(partition, startOffset);
                    log.info("Seeking to offset {} for partition {} of topic {}", startOffset, partition.partition(), topic);
                }
            }

            // Poll messages until we reach the limit or timeout
            long startTime = System.currentTimeMillis();
            long endTime = startTime + timeout;

            while (messages.size() < limit && System.currentTimeMillis() < endTime) {
                long remainingTime = endTime - System.currentTimeMillis();
                if (remainingTime <= 0) {
                    break;
                }

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(Math.min(1000, remainingTime)));

                for (ConsumerRecord<String, String> record : records) {
                    if (messages.size() >= limit) {
                        break;
                    }

                    KafkaMessage message = KafkaMessage.builder()
                            .offset(record.offset())
                            .partition(record.partition())
                            .timestamp(Instant.ofEpochMilli(record.timestamp())
                                    .atZone(ZoneOffset.UTC)
                                    .format(DateTimeFormatter.ISO_INSTANT))
                            .key(record.key())
                            .value(record.value())
                            .topic(record.topic())
                            .build();

                    messages.add(message);
                    log.debug("Consumed message from topic {} partition {} offset {}",
                            record.topic(), record.partition(), record.offset());
                }

                // If no records were received and we haven't reached the limit, break to avoid unnecessary polling
                if (records.isEmpty()) {
                    log.debug("No more records available, stopping poll");
                    break;
                }
            }

            log.info("Successfully consumed {} messages from topic {}", messages.size(), topic);

        } catch (Exception e) {
            log.error("Error consuming messages from topic {}", topic, e);
            throw new RuntimeException("Failed to consume messages from topic: " + topic, e);
        } finally {
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    log.error("Error closing consumer", e);
                }
            }
        }

        return messages;
    }

    /**
     * Create a new Kafka consumer with unique group ID
     */
    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "monitor-service-api-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");

        return new KafkaConsumer<>(props);
    }
}
