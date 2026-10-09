package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.stats.client.AnalyzerClient;
import ru.practicum.stats.client.CollectorClient;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsHelperService {
    private final AnalyzerClient analyzer;
    private final CollectorClient collector;

    public Map<Long, Double> getRatings(Collection<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        return analyzer.interactions(events.stream().map(Event::getId).toList()).stream()
                .collect(Collectors.toMap(RecommendedEventProto::getEventId, RecommendedEventProto::getScore));
    }

    public double getRating(Event event) {
        return getRatings(List.of(event)).getOrDefault(event.getId(), 0.0);
    }

    public void view(long userId, long eventId) {
        collector.collect(userId, eventId, ActionTypeProto.ACTION_VIEW);
    }

    public void like(long userId, long eventId) {
        collector.collect(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }

    public List<RecommendedEventProto> recommendations(long userId, int size) {
        return analyzer.recommendations(userId, size);
    }
}
