package ru.practicum.ewm.stats.aggregator;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ActionListenerTest {
    @Test
    void failedSendIsRetriedWithoutLosingCalculatedUpdate() throws Exception {
        var calculator = mock(SimilarityCalculator.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, SpecificRecordBase> kafka = mock(KafkaTemplate.class);
        var listener = new ActionListener(calculator, kafka);
        ReflectionTestUtils.setField(listener, "topic", "pairs");
        var action = UserActionAvro.newBuilder().setUserId(1L).setEventId(2L)
                .setActionType(ActionTypeAvro.LIKE).setTimestamp(Instant.ofEpochMilli(123)).build();
        var pair = EventSimilarityAvro.newBuilder().setEventA(1L).setEventB(2L).setScore(0.4)
                .setTimestamp(action.getTimestamp()).build();
        when(calculator.update(action)).thenReturn(List.of(pair));
        when(kafka.send("pairs", "1:2", pair))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertThatThrownBy(() -> listener.consume(action)).isInstanceOf(java.util.concurrent.ExecutionException.class);
        listener.consume(action);
        verify(calculator, times(1)).update(action);
        verify(kafka, times(2)).send("pairs", "1:2", pair);
    }

    @Test
    void assignmentResetsMemoryAndRequestsFullReplay() {
        var calculator = mock(SimilarityCalculator.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, SpecificRecordBase> kafka = mock(KafkaTemplate.class);
        var listener = new ActionListener(calculator, kafka);
        var callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
        var assigned = Map.of(new TopicPartition("actions", 0), 100L);
        listener.onPartitionsAssigned(assigned, callback);
        verify(calculator).reset();
        verify(callback).seekToBeginning(assigned.keySet());
    }
}
