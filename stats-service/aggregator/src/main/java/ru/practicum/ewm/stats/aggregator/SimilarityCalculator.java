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
    private volatile State state = new State();

    private static class State {
        private final Map<Long, Map<Long, Double>> weights = new HashMap<>();
        private final Map<Long, Double> totals = new HashMap<>();
        private final Map<Long, Map<Long, Double>> minimums = new HashMap<>();
    }

    public SimilarityCalculator(@Value("${stats.aggregation.publish-all-pairs:false}") boolean publishAllPairs) {
        this.publishAllPairs = publishAllPairs;
    }

    public List<EventSimilarityAvro> update(UserActionAvro action) {
        Calculation calculation = prepare(action);
        calculation.commit().run();
        return calculation.similarities();
    }

    // One consumer, one record per poll: no other calculation runs before delivery completes.
    public Calculation prepare(UserActionAvro action) {
        State snapshot = state;
        var weights = snapshot.weights;
        var totals = snapshot.totals;
        var minimums = snapshot.minimums;
        long eventId = action.getEventId();
        long userId = action.getUserId();
        var users = weights.getOrDefault(eventId, Map.of());
        double previous = users.getOrDefault(userId, 0.0);
        double current = ActionWeights.of(action.getActionType());
        if (current <= previous) {
            return new Calculation(List.of(), () -> { });
        }
        double updatedTotal = totals.getOrDefault(eventId, 0.0) + current - previous;
        Map<Long, Map<Long, Double>> updatedMinimums = new HashMap<>();
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
            var pairs = minimums.getOrDefault(first, Map.of());
            double sum = pairs.getOrDefault(second, 0.0) + delta;
            updatedMinimums.computeIfAbsent(first, id -> new HashMap<>()).put(second, sum);
            if (!publishAllPairs && otherWeight == 0.0) {
                continue;
            }
            // The denominator changes even when this user's common contribution is zero.
            double score = sum / Math.sqrt(updatedTotal * totals.get(otherId));
            result.add(EventSimilarityAvro.newBuilder().setEventA(first).setEventB(second)
                    .setScore(score).setTimestamp(action.getTimestamp()).build());
        }
        return new Calculation(List.copyOf(result), () -> {
            weights.computeIfAbsent(eventId, id -> new HashMap<>()).put(userId, current);
            totals.put(eventId, updatedTotal);
            updatedMinimums.forEach((first, pairs) ->
                    minimums.computeIfAbsent(first, id -> new HashMap<>()).putAll(pairs));
        });
    }

    public record Calculation(List<EventSimilarityAvro> similarities, Runnable commit) {
    }

    public void reset() {
        // A late producer callback can only update its old state, never the new replay.
        state = new State();
    }
}
