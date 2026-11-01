package ru.practicum.ewm.stats.analyzer;

import java.time.Instant;

public record Interaction(long eventId, double weight, Instant timestamp) {
}
