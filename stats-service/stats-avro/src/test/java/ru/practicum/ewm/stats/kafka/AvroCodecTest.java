package ru.practicum.ewm.stats.kafka;

import org.junit.jupiter.api.Test;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroCodecTest {
    @Test
    void userActionRoundTripKeepsIdsTypeAndMillisecondTimestamp() {
        var action = UserActionAvro.newBuilder().setUserId(5_000_000_000L).setEventId(6_000_000_000L)
                .setActionType(ActionTypeAvro.LIKE).setTimestamp(Instant.ofEpochMilli(123456789)).build();
        assertThat(new UserActionDeserializer().deserialize("actions", new AvroSerializer().serialize("actions", action)))
                .isEqualTo(action);
    }

    @Test
    void similarityRoundTripKeepsDoublePrecision() {
        var pair = EventSimilarityAvro.newBuilder().setEventA(1L).setEventB(2L).setScore(0.303030303030303)
                .setTimestamp(Instant.ofEpochMilli(123456789)).build();
        assertThat(new EventSimilarityDeserializer().deserialize("pairs", new AvroSerializer().serialize("pairs", pair)))
                .isEqualTo(pair);
    }

    @Test
    void nullMessagesAreHandledAndCorruptMessagesRejected() {
        assertThat(new AvroSerializer().serialize("actions", null)).isNull();
        assertThat(new UserActionDeserializer().deserialize("actions", null)).isNull();
        assertThatThrownBy(() -> new UserActionDeserializer().deserialize("actions", new byte[]{1}))
                .isInstanceOf(org.apache.kafka.common.errors.SerializationException.class);
    }
}
