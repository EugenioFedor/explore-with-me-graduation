package ru.practicum.ewm.stats.kafka;

import ru.practicum.ewm.stats.avro.ActionTypeAvro;

public final class ActionWeights {
    private ActionWeights() {
    }

    public static double of(ActionTypeAvro type) {
        return switch (type) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }
}
