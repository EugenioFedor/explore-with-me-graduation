package ru.practicum.ewm.stats.aggregator;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ActionListenerTest {
    private final SimilarityCalculator calculator = new SimilarityCalculator(false);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, SpecificRecordBase> kafka = mock(KafkaTemplate.class);
    private final ActionListener listener = new ActionListener(calculator, kafka);

    ActionListenerTest() {
        ReflectionTestUtils.setField(listener, "topic", "pairs");
    }

    private UserActionAvro action(long event, Instant timestamp) {
        return UserActionAvro.newBuilder().setUserId(1L).setEventId(event)
                .setActionType(ActionTypeAvro.LIKE).setTimestamp(timestamp).build();
    }

    @Test
    void doesNotBlockWhileBrokerIsDelivering() {
        var timestamp = Instant.ofEpochMilli(123);
        listener.consume(action(1, timestamp)).join();
        var delivery = new CompletableFuture<SendResult<String, SpecificRecordBase>>();
        when(kafka.send(eq("pairs"), eq("1:2"), any(EventSimilarityAvro.class))).thenReturn(delivery);
        var result = listener.consume(action(2, timestamp));
        assertThat(result).isNotDone();
        assertThat(calculator.prepare(action(2, timestamp)).similarities()).hasSize(1);
        delivery.complete(null);
        assertThat(result).isCompleted();
        assertThat(calculator.prepare(action(2, timestamp)).similarities()).isEmpty();
    }

    @Test
    void failedSendWithDifferentTimestampCanBeRecalculated() {
        var timestamp = Instant.ofEpochMilli(123);
        listener.consume(action(1, timestamp)).join();
        when(kafka.send(eq("pairs"), eq("1:2"), any(EventSimilarityAvro.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertThatThrownBy(() -> listener.consume(action(2, timestamp)).join())
                .hasCauseInstanceOf(IllegalStateException.class);
        listener.consume(action(2, timestamp.plusMillis(1))).join();
        assertThat(calculator.prepare(action(2, timestamp)).similarities()).isEmpty();
        verify(kafka, times(2)).send(eq("pairs"), eq("1:2"), any(EventSimilarityAvro.class));
    }

    @Test
    void synchronousSendFailureDoesNotChangeWeights() {
        var timestamp = Instant.ofEpochMilli(123);
        listener.consume(action(1, timestamp)).join();
        when(kafka.send(eq("pairs"), eq("1:2"), any(EventSimilarityAvro.class)))
                .thenThrow(new IllegalStateException("producer closed"));
        assertThatThrownBy(() -> listener.consume(action(2, timestamp)).join())
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(calculator.prepare(action(2, timestamp)).similarities()).hasSize(1);
    }

    @Test
    void lateCompletionAfterRebalanceCannotModifyNewState() {
        var timestamp = Instant.ofEpochMilli(123);
        listener.consume(action(1, timestamp)).join();
        var delivery = new CompletableFuture<SendResult<String, SpecificRecordBase>>();
        when(kafka.send(eq("pairs"), eq("1:2"), any(EventSimilarityAvro.class))).thenReturn(delivery);
        var result = listener.consume(action(2, timestamp));
        var callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
        var assigned = Map.of(new TopicPartition("actions", 0), 100L);
        listener.onPartitionsAssigned(assigned, callback);
        delivery.complete(null);
        assertThat(result).isCompleted();
        assertThat(calculator.update(action(1, timestamp))).isEmpty();
        assertThat(calculator.update(action(2, timestamp))).hasSize(1);
        verify(callback).seekToBeginning(assigned.keySet());
    }

    @Test
    void waitsForAllPairsBeforeCommittingCalculation() {
        var timestamp = Instant.ofEpochMilli(123);
        listener.consume(action(1, timestamp)).join();
        when(kafka.send(anyString(), anyString(), any(SpecificRecordBase.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        listener.consume(action(2, timestamp)).join();
        var first = new CompletableFuture<SendResult<String, SpecificRecordBase>>();
        var second = new CompletableFuture<SendResult<String, SpecificRecordBase>>();
        when(kafka.send(eq("pairs"), eq("1:3"), any(EventSimilarityAvro.class))).thenReturn(first);
        when(kafka.send(eq("pairs"), eq("2:3"), any(EventSimilarityAvro.class))).thenReturn(second);
        var result = listener.consume(action(3, timestamp));
        first.complete(null);
        assertThat(result).isNotDone();
        second.completeExceptionally(new IllegalStateException("delivery timeout"));
        assertThat(result).isCompletedExceptionally();
        assertThat(calculator.prepare(action(3, timestamp)).similarities()).hasSize(2);
    }
}
