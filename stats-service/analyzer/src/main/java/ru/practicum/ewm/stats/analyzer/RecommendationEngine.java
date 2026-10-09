package ru.practicum.ewm.stats.analyzer;

import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RecommendationEngine {
    private static final Comparator<Recommendation> ORDER = Comparator.comparingDouble(Recommendation::score)
            .reversed().thenComparingLong(Recommendation::eventId);

    public List<Recommendation> similar(long eventId, List<Interaction> history, List<Similarity> pairs, int limit) {
        Set<Long> seen = history.stream().map(Interaction::eventId).collect(Collectors.toSet());
        return pairs.stream().filter(pair -> pair.eventA() == eventId || pair.eventB() == eventId)
                .filter(pair -> pair.score() > 0)
                .map(pair -> new Recommendation(pair.other(eventId), pair.score()))
                .filter(item -> !seen.contains(item.eventId()) && item.eventId() != eventId)
                .sorted(ORDER).limit(limit).toList();
    }

    public List<Recommendation> predict(List<Interaction> history, List<Similarity> pairs,
                                       int limit, int recentLimit, int neighborsLimit) {
        if (history.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> ratings = history.stream()
                .collect(Collectors.toMap(Interaction::eventId, Interaction::weight));
        Set<Long> recent = history.stream().sorted(Comparator.comparing(Interaction::timestamp).reversed()
                        .thenComparingLong(Interaction::eventId))
                .limit(recentLimit).map(Interaction::eventId).collect(Collectors.toSet());
        Map<Long, Double> candidates = new HashMap<>();
        for (Similarity pair : pairs) {
            if (pair.score() <= 0) {
                continue;
            }
            if (recent.contains(pair.eventA()) && !ratings.containsKey(pair.eventB())) {
                candidates.merge(pair.eventB(), pair.score(), Math::max);
            }
            if (recent.contains(pair.eventB()) && !ratings.containsKey(pair.eventA())) {
                candidates.merge(pair.eventA(), pair.score(), Math::max);
            }
        }
        return candidates.entrySet().stream().map(e -> new Recommendation(e.getKey(), e.getValue()))
                .sorted(ORDER).limit(limit)
                .map(candidate -> prediction(candidate.eventId(), pairs, ratings, neighborsLimit))
                .filter(item -> item.score() > 0).sorted(ORDER).toList();
    }

    private Recommendation prediction(long eventId, List<Similarity> pairs, Map<Long, Double> ratings, int limit) {
        List<Similarity> neighbors = pairs.stream()
                .filter(pair -> (pair.eventA() == eventId || pair.eventB() == eventId)
                        && pair.score() > 0 && ratings.containsKey(pair.other(eventId)))
                .sorted(Comparator.comparingDouble(Similarity::score).reversed()
                        .thenComparingLong(pair -> pair.other(eventId)))
                .limit(limit).toList();
        double denominator = neighbors.stream().mapToDouble(Similarity::score).sum();
        double numerator = neighbors.stream().mapToDouble(pair -> pair.score() * ratings.get(pair.other(eventId))).sum();
        return new Recommendation(eventId, denominator == 0 ? 0 : numerator / denominator);
    }
}
