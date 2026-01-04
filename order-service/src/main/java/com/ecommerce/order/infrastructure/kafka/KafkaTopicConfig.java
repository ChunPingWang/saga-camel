package com.ecommerce.order.infrastructure.kafka;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka topic configuration for Saga CDC messaging.
 * Creates topics programmatically if they don't exist.
 */
@Configuration
@ConditionalOnProperty(name = "saga.kafka.topics.auto-create", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${saga.kafka.topics.partitions:3}")
    private int partitions;

    @Value("${saga.kafka.topics.replication-factor:1}")
    private short replicationFactor;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    // Command topics

    @Bean
    public NewTopic creditCardCommandsTopic(
            @Value("${saga.kafka.topics.credit-card-commands:saga.credit-card.commands}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic inventoryCommandsTopic(
            @Value("${saga.kafka.topics.inventory-commands:saga.inventory.commands}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic logisticsCommandsTopic(
            @Value("${saga.kafka.topics.logistics-commands:saga.logistics.commands}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    // Response topics

    @Bean
    public NewTopic creditCardResponsesTopic(
            @Value("${saga.kafka.topics.credit-card-responses:saga.credit-card.responses}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic inventoryResponsesTopic(
            @Value("${saga.kafka.topics.inventory-responses:saga.inventory.responses}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic logisticsResponsesTopic(
            @Value("${saga.kafka.topics.logistics-responses:saga.logistics.responses}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    // Dead letter queue

    @Bean
    public NewTopic dlqTopic(
            @Value("${saga.kafka.topics.dlq:saga.dlq}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
