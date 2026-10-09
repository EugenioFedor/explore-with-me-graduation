package ru.practicum.ewm.stats.analyzer;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecommendationEngineTest {
    private final RecommendationEngine engine = new RecommendationEngine();

    private Interaction history(long eventId, double weight, int time) {
        return new Interaction(eventId, weight, Instant.ofEpochSecond(time));
    }

    @Test
    void coldUserHasNoPredictions() {
        assertThat(engine.predict(List.of(), List.of(new Similarity(1, 2, 1)), 10, 10, 10)).isEmpty();
    }

    @Test
    void predictsWeightedAverageFromTheoryExample() {
        var history = List.of(history(2, 0.8, 1), history(3, 0.4, 2), history(4, 0.8, 3));
        var pairs = List.of(new Similarity(1, 2, 0.9), new Similarity(1, 3, 0.7), new Similarity(1, 4, 0.6));
        var result = engine.predict(history, pairs, 10, 100, 10);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().eventId()).isEqualTo(1);
        assertThat(result.getFirst().score()).isCloseTo(1.48 / 2.2, within(1e-12));
    }

    @Test
    void limitsNeighborsToMostSimilarPreviouslyInteractedEvents() {
        var history = List.of(history(2, 0.8, 1), history(3, 0.4, 2));
        var pairs = List.of(new Similarity(1, 2, 0.9), new Similarity(1, 3, 0.7));
        assertThat(engine.predict(history, pairs, 10, 100, 1).getFirst().score()).isCloseTo(0.8, within(1e-12));
    }

    @Test
    void usesRecentHistoryForCandidatesButExcludesAllInteractedEvents() {
        var history = List.of(history(2, 0.8, 1), history(3, 0.4, 2));
        var pairs = List.of(new Similarity(1, 2, 0.9), new Similarity(2, 3, 0.7), new Similarity(3, 4, 0.6));
        var result = engine.predict(history, pairs, 10, 1, 10);
        assertThat(result).extracting(Recommendation::eventId).containsExactly(4L);
    }

    @Test
    void deduplicatesCandidatesAndHonorsResultLimit() {
        var history = List.of(history(2, 0.8, 1), history(3, 0.4, 2));
        var pairs = List.of(new Similarity(1, 2, 0.9), new Similarity(1, 3, 0.7), new Similarity(3, 4, 0.6));
        assertThat(engine.predict(history, pairs, 1, 10, 10)).extracting(Recommendation::eventId).containsExactly(1L);
    }

    @Test
    void similarUsesEitherPairSideAndExcludesSeenWithoutRequiringSourceToBeSeen() {
        var pairs = List.of(new Similarity(1, 3, 0.9), new Similarity(3, 4, 0.8), new Similarity(3, 5, 0.7));
        assertThat(engine.similar(3, List.of(history(4, 0.4, 1)), pairs, 10))
                .extracting(Recommendation::eventId).containsExactly(1L, 5L);
    }

    @Test
    void zeroSimilarityAndZeroLimitReturnNoRecommendations() {
        var history = List.of(history(1, 0.4, 1));
        assertThat(engine.predict(history, List.of(new Similarity(1, 2, 0)), 10, 10, 10)).isEmpty();
        assertThat(engine.predict(history, List.of(new Similarity(1, 2, 1)), 0, 10, 10)).isEmpty();
        assertThat(engine.similar(1, history, List.of(new Similarity(1, 2, 0)), 10)).isEmpty();
    }

    @Test
    void ordersPredictionsByScoreWithStableTieBreak() {
        var history = List.of(history(1, 1.0, 1), history(2, 0.4, 2));
        var pairs = List.of(new Similarity(1, 3, 0.6), new Similarity(2, 4, 0.9), new Similarity(1, 5, 0.6));
        assertThat(engine.predict(history, pairs, 10, 10, 10))
                .extracting(Recommendation::eventId).containsExactly(3L, 5L, 4L);
    }
}
