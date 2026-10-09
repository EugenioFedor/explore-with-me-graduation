package ru.practicum.ewm.stats.kafka;

import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AvroSerializer implements Serializer<SpecificRecordBase> {
    @Override
    public byte[] serialize(String topic, SpecificRecordBase value) {
        if (value == null) {
            return null;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var encoder = EncoderFactory.get().binaryEncoder(output, null);
            new SpecificDatumWriter<SpecificRecordBase>(value.getSchema()).write(value, encoder);
            encoder.flush();
            return output.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Cannot encode Avro record", e);
        }
    }
}
