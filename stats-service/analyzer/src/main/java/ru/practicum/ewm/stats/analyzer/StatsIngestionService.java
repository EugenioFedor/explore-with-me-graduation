package ru.practicum.ewm.stats.analyzer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsIngestionService {
    private final StatsRepository repository;

    @Transactional
    public void saveActions(List<UserActionAvro> actions) {
        if (!actions.isEmpty()) {
            repository.saveActions(actions);
        }
    }

    @Transactional
    public void saveSimilarities(List<EventSimilarityAvro> similarities) {
        if (!similarities.isEmpty()) {
            repository.saveSimilarities(similarities);
        }
    }
}
