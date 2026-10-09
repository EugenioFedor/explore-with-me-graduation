package ru.practicum.ewm.stats.analyzer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Component
@RequiredArgsConstructor
public class StatsListeners {
    private final StatsRepository repository;

    @KafkaListener(topics = "${stats.topics.user-actions}")
    public void action(UserActionAvro action) {
        repository.saveAction(action);
    }

    @KafkaListener(topics = "${stats.topics.events-similarity}",
            properties = {"spring.deserializer.value.delegate.class=ru.practicum.ewm.stats.kafka.EventSimilarityDeserializer",
                    "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer"})
    public void similarity(EventSimilarityAvro similarity) {
        repository.saveSimilarity(similarity);
    }
}
