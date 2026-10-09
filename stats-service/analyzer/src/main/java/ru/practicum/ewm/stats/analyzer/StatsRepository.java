package ru.practicum.ewm.stats.analyzer;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.kafka.ActionWeights;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class StatsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public void saveAction(UserActionAvro action) {
        jdbc.update("""
                INSERT INTO user_interactions(user_id, event_id, weight, interacted_at)
                VALUES (:user, :event, :weight, :timestamp)
                ON CONFLICT (user_id, event_id) DO UPDATE
                SET weight = GREATEST(user_interactions.weight, EXCLUDED.weight),
                    interacted_at = GREATEST(user_interactions.interacted_at, EXCLUDED.interacted_at)
                """, Map.of("user", action.getUserId(), "event", action.getEventId(),
                "weight", ActionWeights.of(action.getActionType()), "timestamp", Timestamp.from(action.getTimestamp())));
    }

    public void saveSimilarity(EventSimilarityAvro pair) {
        // Canonical pair key also protects against reversed pairs from external producers.
        jdbc.update("""
                INSERT INTO event_similarities(event_a, event_b, score, calculated_at)
                VALUES (:first, :second, :score, :timestamp)
                ON CONFLICT (event_a, event_b) DO UPDATE
                SET score = EXCLUDED.score, calculated_at = EXCLUDED.calculated_at
                """, Map.of("first", Math.min(pair.getEventA(), pair.getEventB()),
                "second", Math.max(pair.getEventA(), pair.getEventB()), "score", pair.getScore(),
                "timestamp", Timestamp.from(pair.getTimestamp())));
    }

    public List<Interaction> history(long userId) {
        return jdbc.query("SELECT event_id, weight, interacted_at FROM user_interactions WHERE user_id = :user",
                Map.of("user", userId), (rs, row) -> new Interaction(rs.getLong("event_id"),
                        rs.getDouble("weight"), rs.getTimestamp("interacted_at").toInstant()));
    }

    public List<Similarity> similarities(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT event_a, event_b, score FROM event_similarities
                WHERE (event_a IN (:ids) OR event_b IN (:ids)) AND score > 0
                """, Map.of("ids", eventIds), (rs, row) -> new Similarity(rs.getLong("event_a"),
                        rs.getLong("event_b"), rs.getDouble("score")));
    }

    public List<Recommendation> interactions(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> totals = new java.util.HashMap<>();
        jdbc.query("SELECT event_id, SUM(weight) AS total FROM user_interactions WHERE event_id IN (:ids) GROUP BY event_id",
                Map.of("ids", eventIds), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        totals.put(rs.getLong("event_id"), rs.getDouble("total")));
        return eventIds.stream().distinct().map(id -> new Recommendation(id, totals.getOrDefault(id, 0.0))).toList();
    }
}
