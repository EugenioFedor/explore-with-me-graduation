package ru.practicum.ewm.stats.analyzer;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Component
@RequiredArgsConstructor
public class StatsListeners {
    private final StatsIngestionService service;

    @KafkaListener(topics = "${stats.topics.user-actions}", batch = "true")
    public void action(List<UserActionAvro> actions) {
        service.saveActions(actions);
    }

    @KafkaListener(topics = "${stats.topics.events-similarity}", batch = "true",
            properties = {"spring.deserializer.value.delegate.class=ru.practicum.ewm.stats.kafka.EventSimilarityDeserializer",
                    "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer"})
    public void similarity(List<EventSimilarityAvro> similarities) {
        service.saveSimilarities(similarities);
    }
}
