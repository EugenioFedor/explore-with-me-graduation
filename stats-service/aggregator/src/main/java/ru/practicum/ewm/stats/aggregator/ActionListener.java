package ru.practicum.ewm.stats.aggregator;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ActionListener implements ConsumerSeekAware {
    private final SimilarityCalculator calculator;
    private final KafkaTemplate<String, SpecificRecordBase> kafka;
    @Value("${stats.topics.events-similarity}")
    private String topic;

    @KafkaListener(id = "action-consumer", idIsGroup = false, concurrency = "1",
            topics = "${stats.topics.user-actions}", errorHandler = "deliveryErrorHandler")
    public CompletableFuture<Void> consume(UserActionAvro action) {
        var calculation = calculator.prepare(action);
        try {
            CompletableFuture<?>[] delivery = calculation.similarities().stream()
                    .map(pair -> kafka.send(topic, pair.getEventA() + ":" + pair.getEventB(), pair))
                    .toArray(CompletableFuture<?>[]::new);
            // Kafka retries delivery within its configured timeout. Commit and acknowledge only on success.
            return CompletableFuture.allOf(delivery).thenRun(calculation.commit());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        calculator.reset();
        callback.seekToBeginning(assignments.keySet());
    }
}
