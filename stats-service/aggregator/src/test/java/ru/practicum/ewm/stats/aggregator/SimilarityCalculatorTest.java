package ru.practicum.ewm.stats.aggregator;

import org.junit.jupiter.api.Test;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.kafka.ActionWeights;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SimilarityCalculatorTest {
    private final SimilarityCalculator calculator = new SimilarityCalculator(true);

    private UserActionAvro action(long user, long event, ActionTypeAvro type) {
        return UserActionAvro.newBuilder().setUserId(user).setEventId(event).setActionType(type)
                .setTimestamp(Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    @Test
    void courseModePublishesOnlyPairsInteractedWithByCurrentUser() {
        var course = new SimilarityCalculator(false);
        course.update(action(1, 10, ActionTypeAvro.VIEW));
        assertThat(course.update(action(2, 20, ActionTypeAvro.VIEW))).isEmpty();
        var pair = course.update(action(1, 20, ActionTypeAvro.LIKE)).getFirst();
        assertThat(pair.getScore()).isCloseTo(0.4 / Math.sqrt(0.4 * 1.4), within(1e-12));
        assertThat(pair.getEventA()).isEqualTo(10);
        assertThat(pair.getEventB()).isEqualTo(20);
    }

    @Test
    void firstEventHasNoSelfSimilarity() {
        assertThat(calculator.update(action(1, 10, ActionTypeAvro.VIEW))).isEmpty();
    }

    @Test
    void usesMinimumAndLinearWeightSumsWithCanonicalIds() {
        calculator.update(action(1, 20, ActionTypeAvro.LIKE));
        var pair = calculator.update(action(1, 10, ActionTypeAvro.REGISTER)).getFirst();
        assertThat(pair.getEventA()).isEqualTo(10);
        assertThat(pair.getEventB()).isEqualTo(20);
        assertThat(pair.getScore()).isCloseTo(0.8 / Math.sqrt(0.8), within(1e-12));
        assertThat(pair.getTimestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void repeatedOrLowerWeightDoesNotRecalculate() {
        calculator.update(action(1, 10, ActionTypeAvro.LIKE));
        calculator.update(action(1, 20, ActionTypeAvro.VIEW));
        assertThat(calculator.update(action(1, 10, ActionTypeAvro.VIEW))).isEmpty();
        assertThat(calculator.update(action(1, 10, ActionTypeAvro.LIKE))).isEmpty();
    }

    @Test
    void updatesDenominatorEvenIfUserDidNotInteractWithOtherEvent() {
        calculator.update(action(1, 10, ActionTypeAvro.LIKE));
        calculator.update(action(1, 20, ActionTypeAvro.LIKE));
        var pair = calculator.update(action(2, 10, ActionTypeAvro.LIKE)).getFirst();
        assertThat(pair.getScore()).isCloseTo(1 / Math.sqrt(2), within(1e-12));
    }

    @Test
    void updatesDenominatorWhenMinimumContributionDoesNotChange() {
        calculator.update(action(1, 10, ActionTypeAvro.REGISTER));
        calculator.update(action(1, 20, ActionTypeAvro.VIEW));
        var pair = calculator.update(action(1, 10, ActionTypeAvro.LIKE)).getFirst();
        assertThat(pair.getScore()).isCloseTo(0.4 / Math.sqrt(0.4), within(1e-12));
    }

    @Test
    void unrelatedEventsHaveZeroSimilarity() {
        calculator.update(action(1, 10, ActionTypeAvro.LIKE));
        assertThat(calculator.update(action(2, 20, ActionTypeAvro.LIKE)).getFirst().getScore()).isZero();
    }

    @Test
    void resetAndReplayRebuildIdenticalState() {
        var first = action(1, 10, ActionTypeAvro.VIEW);
        var second = action(1, 20, ActionTypeAvro.REGISTER);
        calculator.update(first);
        var expected = calculator.update(second);
        calculator.reset();
        calculator.update(first);
        assertThat(calculator.update(second)).isEqualTo(expected);
    }

    @Test
    void incrementalResultsMatchIndependentBatchFormulaForRandomActions() {
        Random random = new Random(42);
        Map<Long, Map<Long, Double>> weights = new HashMap<>();
        for (int i = 0; i < 500; i++) {
            long user = 1 + random.nextInt(15);
            long event = 1 + random.nextInt(20);
            var type = ActionTypeAvro.values()[random.nextInt(3)];
            var users = weights.computeIfAbsent(event, id -> new HashMap<>());
            double old = users.getOrDefault(user, 0.0);
            users.merge(user, ActionWeights.of(type), Math::max);
            var pairs = calculator.update(action(user, event, type));
            if (old >= ActionWeights.of(type)) {
                assertThat(pairs).isEmpty();
                continue;
            }
            assertThat(pairs).hasSize(weights.size() - 1);
            for (var pair : pairs) {
                var a = weights.get(pair.getEventA());
                var b = weights.get(pair.getEventB());
                double sum = a.entrySet().stream().mapToDouble(e -> Math.min(e.getValue(), b.getOrDefault(e.getKey(), 0.0))).sum();
                double totalA = a.values().stream().mapToDouble(Double::doubleValue).sum();
                double totalB = b.values().stream().mapToDouble(Double::doubleValue).sum();
                assertThat(pair.getScore()).isCloseTo(sum / Math.sqrt(totalA * totalB), within(1e-12));
            }
        }
    }
}
