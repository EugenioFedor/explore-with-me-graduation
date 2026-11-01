package ru.practicum.ewm.stats.kafka;

import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import java.io.IOException;

public abstract class AvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {
    private final Class<T> recordClass;

    protected AvroDeserializer(Class<T> recordClass) {
        this.recordClass = recordClass;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return new SpecificDatumReader<>(recordClass).read(null, DecoderFactory.get().binaryDecoder(data, null));
        } catch (IOException e) {
            throw new SerializationException("Cannot decode Avro record", e);
        }
    }
}
