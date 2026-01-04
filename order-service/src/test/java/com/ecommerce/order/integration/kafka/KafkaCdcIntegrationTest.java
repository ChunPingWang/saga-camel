package com.ecommerce.order.integration.kafka;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.common.kafka.SagaMessage;
import com.ecommerce.order.adapter.in.kafka.SagaKafkaMessage;
import com.ecommerce.order.adapter.out.persistence.OutboxEventEntity;
import com.ecommerce.order.adapter.out.persistence.OutboxEventRepository;
import com.ecommerce.order.adapter.out.persistence.TransactionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Kafka CDC functionality.
 * Uses Testcontainers to spin up PostgreSQL and Kafka.
 *
 * Run with: ./gradlew :order-service:test --tests "*KafkaCdcIntegrationTest*" -Dinclude.integration=true
 *
 * Prerequisites:
 * - Docker running (Rancher Desktop users: set DOCKER_HOST env var)
 * - Testcontainers configured for your Docker environment
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("kafka-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
@Disabled("Disabled by default - requires Docker. Run with -Dinclude.integration=true")
class KafkaCdcIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("sagadb")
            .withUsername("saga")
            .withPassword("saga_password")
            .withCommand("postgres", "-c", "wal_level=logical");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("saga.messaging.type", () -> "kafka");
        registry.add("saga.kafka.enabled", () -> "true");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionLogRepository transactionLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, SagaMessage> consumer;
    private Producer<String, SagaMessage> producer;

    @BeforeEach
    void setUp() {
        // Set up Kafka consumer for testing
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ecommerce.*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SagaMessage.class.getName());

        consumer = new DefaultKafkaConsumerFactory<String, SagaMessage>(consumerProps).createConsumer();

        // Set up Kafka producer for testing
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", JsonSerializer.class);

        producer = new DefaultKafkaProducerFactory<String, SagaMessage>(producerProps).createProducer();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("應該能夠建立 Outbox 事件")
    void shouldCreateOutboxEvent() {
        // Given
        String txId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "totalAmount", 1000.00,
                "items", Collections.emptyList()
        );

        // When
        OutboxEventEntity event = new OutboxEventEntity(
                txId,
                orderId,
                "SAGA_STARTED",
                toJson(payload),
                "saga",
                orderId
        );
        outboxEventRepository.save(event);

        // Then
        assertThat(event.getId()).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo("saga");
        assertThat(event.getAggregateId()).isEqualTo(orderId);
    }

    @Test
    @Order(2)
    @DisplayName("應該能夠發送 Saga 命令到 Kafka")
    void shouldSendSagaCommandToKafka() throws Exception {
        // Given
        String txId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String topic = "saga.credit-card.commands";

        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "totalAmount", 500.00,
                "creditCardNumber", "4111111111111111"
        );

        SagaMessage command = SagaMessage.executeCommand(
                txId, orderId, ServiceName.CREDIT_CARD, payload);

        consumer.subscribe(Collections.singletonList(topic));

        // When
        producer.send(new ProducerRecord<>(topic, orderId, command)).get(10, TimeUnit.SECONDS);

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, SagaMessage> record = records.iterator().next();
            assertThat(record.key()).isEqualTo(orderId);
            assertThat(record.value().getTxId()).isEqualTo(txId);
            assertThat(record.value().getServiceName()).isEqualTo(ServiceName.CREDIT_CARD);
            assertThat(record.value().getMessageType()).isEqualTo(SagaMessage.MessageType.EXECUTE_COMMAND);
        });
    }

    @Test
    @Order(3)
    @DisplayName("應該能夠處理 Saga 成功回應")
    void shouldHandleSagaSuccessResponse() throws Exception {
        // Given
        String txId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String topic = "saga.credit-card.responses";

        SagaMessage response = SagaMessage.successResponse(
                txId, orderId, ServiceName.CREDIT_CARD,
                "AUTH-12345678", "Payment captured", false);

        consumer.subscribe(Collections.singletonList(topic));

        // When
        producer.send(new ProducerRecord<>(topic, orderId, response)).get(10, TimeUnit.SECONDS);

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, SagaMessage> record = records.iterator().next();
            assertThat(record.value().isSuccess()).isTrue();
            assertThat(record.value().getServiceReference()).isEqualTo("AUTH-12345678");
        });
    }

    @Test
    @Order(4)
    @DisplayName("應該能夠處理 Saga 失敗回應")
    void shouldHandleSagaFailureResponse() throws Exception {
        // Given
        String txId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String topic = "saga.inventory.responses";

        SagaMessage response = SagaMessage.failureResponse(
                txId, orderId, ServiceName.INVENTORY,
                "Insufficient stock", "STOCK_UNAVAILABLE", false);

        consumer.subscribe(Collections.singletonList(topic));

        // When
        producer.send(new ProducerRecord<>(topic, orderId, response)).get(10, TimeUnit.SECONDS);

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, SagaMessage> record = records.iterator().next();
            assertThat(record.value().isFailure()).isTrue();
            assertThat(record.value().getErrorMessage()).isEqualTo("Insufficient stock");
            assertThat(record.value().getErrorCode()).isEqualTo("STOCK_UNAVAILABLE");
        });
    }

    @Test
    @Order(5)
    @DisplayName("應該能夠處理回滾命令")
    void shouldHandleRollbackCommand() throws Exception {
        // Given
        String txId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String topic = "saga.credit-card.commands";

        SagaMessage rollbackCommand = SagaMessage.rollbackCommand(
                txId, orderId, ServiceName.CREDIT_CARD, "AUTH-12345678");

        consumer.subscribe(Collections.singletonList(topic));

        // When
        producer.send(new ProducerRecord<>(topic, orderId, rollbackCommand)).get(10, TimeUnit.SECONDS);

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, SagaMessage> record = records.iterator().next();
            assertThat(record.value().isRollback()).isTrue();
            assertThat(record.value().getMessageType()).isEqualTo(SagaMessage.MessageType.ROLLBACK_COMMAND);
            assertThat(record.value().getServiceReference()).isEqualTo("AUTH-12345678");
        });
    }

    @Test
    @Order(6)
    @DisplayName("Kafka 訊息應該按 orderId 分區以確保順序")
    void shouldPartitionByOrderIdForOrdering() throws Exception {
        // Given
        String orderId = UUID.randomUUID().toString();
        String topic = "saga.logistics.commands";

        // Send multiple messages with same orderId
        for (int i = 0; i < 5; i++) {
            String txId = UUID.randomUUID().toString();
            SagaMessage command = SagaMessage.executeCommand(
                    txId, orderId, ServiceName.LOGISTICS,
                    Map.of("sequence", i));

            producer.send(new ProducerRecord<>(topic, orderId, command)).get(5, TimeUnit.SECONDS);
        }

        consumer.subscribe(Collections.singletonList(topic));

        // Then - all messages should be in the same partition
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(5);

            // Verify all records with same key are in same partition
            Integer partition = null;
            for (ConsumerRecord<String, SagaMessage> record : records) {
                if (record.key().equals(orderId)) {
                    if (partition == null) {
                        partition = record.partition();
                    } else {
                        assertThat(record.partition()).isEqualTo(partition);
                    }
                }
            }
        });
    }

    @Test
    @Order(7)
    @DisplayName("SagaMessage 應該正確序列化和反序列化")
    void shouldSerializeAndDeserializeSagaMessage() throws Exception {
        // Given
        String txId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "totalAmount", 1500.50,
                "items", Collections.singletonList(Map.of(
                        "sku", "PROD-001",
                        "quantity", 2,
                        "unitPrice", 750.25
                ))
        );

        SagaMessage original = SagaMessage.executeCommand(
                txId, orderId, ServiceName.CREDIT_CARD, payload);
        original.setRetryCount(3);
        original.setProcessingTimeMs(150L);

        // When - serialize
        String json = objectMapper.writeValueAsString(original);

        // Then - deserialize
        SagaMessage deserialized = objectMapper.readValue(json, SagaMessage.class);

        assertThat(deserialized.getTxId()).isEqualTo(txId);
        assertThat(deserialized.getOrderId()).isEqualTo(orderId);
        assertThat(deserialized.getServiceName()).isEqualTo(ServiceName.CREDIT_CARD);
        assertThat(deserialized.getMessageType()).isEqualTo(SagaMessage.MessageType.EXECUTE_COMMAND);
        assertThat(deserialized.getPayload()).isNotNull();
        assertThat(deserialized.getRetryCount()).isEqualTo(3);
        assertThat(deserialized.getProcessingTimeMs()).isEqualTo(150L);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
