package ru.practicum.ewm.stats.aggregator;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ActionListener implements ConsumerSeekAware {
    private final SimilarityCalculator calculator;
    private final KafkaTemplate<String, SpecificRecordBase> kafka;
    @Value("${stats.topics.events-similarity}")
    private String topic;
    private List<EventSimilarityAvro> pending = List.of();
    private UserActionAvro pendingAction;

    @KafkaListener(topics = "${stats.topics.user-actions}")
    public void consume(UserActionAvro action) throws Exception {
        // On send failure, retry the same calculated output without applying the weight twice.
        if (!action.equals(pendingAction)) {
            pending = calculator.update(action);
            pendingAction = action;
        }
        CompletableFuture<?>[] delivery = pending.stream()
                .map(similarity -> kafka.send(topic, similarity.getEventA() + ":" + similarity.getEventB(), similarity))
                .toArray(CompletableFuture<?>[]::new);
        CompletableFuture.allOf(delivery).get(10, TimeUnit.SECONDS);
        pending = List.of();
        pendingAction = null;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        // Single input partition and one aggregator: rebuild in-memory sums after restart/rebalance.
        calculator.reset();
        pending = List.of();
        pendingAction = null;
        callback.seekToBeginning(assignments.keySet());
    }
}
