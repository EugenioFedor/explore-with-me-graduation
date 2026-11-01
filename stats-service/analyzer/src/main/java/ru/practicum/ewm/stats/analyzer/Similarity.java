package ru.practicum.ewm.stats.analyzer;

public record Similarity(long eventA, long eventB, double score) {
    public long other(long eventId) {
        return eventA == eventId ? eventB : eventA;
    }
}
