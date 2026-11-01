package ru.practicum.ewm.stats.aggregator;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.kafka.AvroSerializer;
import ru.practicum.ewm.stats.kafka.UserActionDeserializer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AsyncDeliveryIntegrationTest.Config.class)
@EmbeddedKafka(partitions = 1, topics = {"review-actions", "review-pairs"}, kraft = true)
@TestPropertySource(properties = {"stats.topics.user-actions=review-actions",
        "stats.topics.events-similarity=review-pairs"})
@DirtiesContext
class AsyncDeliveryIntegrationTest {
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    @Autowired
    private KafkaTemplate<String, SpecificRecordBase> output;

    private UserActionAvro action(long event) {
        return UserActionAvro.newBuilder().setUserId(1L).setEventId(event)
                .setActionType(ActionTypeAvro.LIKE).setTimestamp(Instant.ofEpochMilli(event)).build();
    }

    @Test
    void acknowledgesOnlyAfterDeliveryAndStopsWithoutAcknowledgingFailedRecord() throws Exception {
        Map<String, Object> producerProperties = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                broker.getBrokersAsString(), ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroSerializer.class);
        var producerFactory = new DefaultKafkaProducerFactory<Long, SpecificRecordBase>(producerProperties);
        var input = new KafkaTemplate<>(producerFactory);
        var container = registry.getListenerContainer("action-consumer");
        var partition = new TopicPartition("review-actions", 0);
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
            when(output.send(anyString(), anyString(), any(SpecificRecordBase.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));
            input.send("review-actions", 1L, action(1)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(admin.listConsumerGroupOffsets("review-async").partitionsToOffsetAndMetadata()
                            .get(5, TimeUnit.SECONDS).getOrDefault(partition,
                                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0L)).offset()).isEqualTo(1L));
            var delivery = new CompletableFuture<SendResult<String, SpecificRecordBase>>();
            when(output.send(eq("review-pairs"), eq("1:2"), any(SpecificRecordBase.class))).thenReturn(delivery);
            input.send("review-actions", 1L, action(2)).get(10, TimeUnit.SECONDS);
            input.send("review-actions", 1L, action(3)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    verify(output).send(eq("review-pairs"), eq("1:2"), any(SpecificRecordBase.class)));
            assertThat(admin.listConsumerGroupOffsets("review-async").partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS).getOrDefault(partition,
                                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0L)).offset()).isEqualTo(1L);
            verify(output, never()).send(eq("review-pairs"), eq("1:3"), any(SpecificRecordBase.class));
            delivery.complete(null);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(admin.listConsumerGroupOffsets("review-async").partitionsToOffsetAndMetadata()
                            .get(5, TimeUnit.SECONDS).getOrDefault(partition,
                                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0L)).offset()).isEqualTo(3L));
            when(output.send(eq("review-pairs"), eq("1:4"), any(SpecificRecordBase.class)))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
            input.send("review-actions", 1L, action(4)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20)).until(() -> !container.isRunning());
            assertThat(admin.listConsumerGroupOffsets("review-async").partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS).getOrDefault(partition,
                                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0L)).offset()).isEqualTo(3L);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(admin.describeConsumerGroups(java.util.List.of("review-async"))
                            .describedGroups().get("review-async").get(5, TimeUnit.SECONDS).members()).isEmpty());
            // Recovery replays committed history and processes the unacknowledged action.
            when(output.send(anyString(), anyString(), any(SpecificRecordBase.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));
            container.start();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(admin.listConsumerGroupOffsets("review-async").partitionsToOffsetAndMetadata()
                            .get(5, TimeUnit.SECONDS).getOrDefault(partition,
                                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(0L)).offset()).isEqualTo(4L));
        } finally {
            producerFactory.destroy();
        }
    }

    @Configuration
    @EnableKafka
    @Import({ActionListener.class, KafkaErrorConfig.class})
    static class Config {
        @Bean
        SimilarityCalculator calculator() {
            return new SimilarityCalculator(false);
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, SpecificRecordBase> output() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<Long, UserActionAvro> kafkaListenerContainerFactory(
                EmbeddedKafkaBroker broker) {
            var properties = new HashMap<String, Object>();
            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, "review-async");
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
            var factory = new ConcurrentKafkaListenerContainerFactory<Long, UserActionAvro>();
            factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties,
                    new LongDeserializer(), new UserActionDeserializer()));
            return factory;
        }
    }
}
