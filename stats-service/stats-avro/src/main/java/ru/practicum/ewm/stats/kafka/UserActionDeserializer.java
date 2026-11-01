package ru.practicum.ewm.stats.kafka;

import ru.practicum.ewm.stats.avro.UserActionAvro;

public class UserActionDeserializer extends AvroDeserializer<UserActionAvro> {
    public UserActionDeserializer() {
        super(UserActionAvro.class);
    }
}
