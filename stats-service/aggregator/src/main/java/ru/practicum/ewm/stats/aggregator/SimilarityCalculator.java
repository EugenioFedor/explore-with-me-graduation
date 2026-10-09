package ru.practicum.ewm.stats.aggregator;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.kafka.ActionWeights;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SimilarityCalculator {
    private final boolean publishAllPairs;
    private final Map<Long, Map<Long, Double>> weights = new HashMap<>();
    private final Map<Long, Double> totals = new HashMap<>();
    private final Map<Long, Map<Long, Double>> minimums = new HashMap<>();

    public SimilarityCalculator(@Value("${stats.aggregation.publish-all-pairs:false}") boolean publishAllPairs) {
        this.publishAllPairs = publishAllPairs;
    }

    public synchronized List<EventSimilarityAvro> update(UserActionAvro action) {
        long eventId = action.getEventId();
        long userId = action.getUserId();
        var users = weights.computeIfAbsent(eventId, id -> new HashMap<>());
        double previous = users.getOrDefault(userId, 0.0);
        double current = ActionWeights.of(action.getActionType());
        if (current <= previous) {
            return List.of();
        }
        users.put(userId, current);
        totals.merge(eventId, current - previous, Double::sum);
        List<EventSimilarityAvro> result = new ArrayList<>();
        for (var other : weights.entrySet()) {
            long otherId = other.getKey();
            if (otherId == eventId) {
                continue;
            }
            long first = Math.min(eventId, otherId);
            long second = Math.max(eventId, otherId);
            double otherWeight = other.getValue().getOrDefault(userId, 0.0);
            double delta = Math.min(current, otherWeight) - Math.min(previous, otherWeight);
            var pairs = minimums.computeIfAbsent(first, id -> new HashMap<>());
            double sum = pairs.getOrDefault(second, 0.0) + delta;
            pairs.put(second, sum);
            if (!publishAllPairs && otherWeight == 0.0) {
                continue;
            }
            // The denominator changes even when this user's common contribution is zero.
            double score = sum / Math.sqrt(totals.get(eventId) * totals.get(otherId));
            result.add(EventSimilarityAvro.newBuilder().setEventA(first).setEventB(second)
                    .setScore(score).setTimestamp(action.getTimestamp()).build());
        }
        return result;
    }

    public synchronized void reset() {
        weights.clear();
        totals.clear();
        minimums.clear();
    }
}
