package ru.practicum.ewm.stats.collector;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.ewm.stats.proto.collector.UserActionControllerGrpc;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@GrpcService
@RequiredArgsConstructor
public class UserActionGrpcController extends UserActionControllerGrpc.UserActionControllerImplBase {
    private final KafkaTemplate<Long, SpecificRecordBase> kafka;
    @Value("${stats.topics.user-actions}")
    private String topic;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> observer) {
        if (request.getUserId() <= 0 || request.getEventId() <= 0 || !request.hasTimestamp()
                || request.getActionType() == ActionTypeProto.UNRECOGNIZED
                || request.getTimestamp().getNanos() < 0 || request.getTimestamp().getNanos() > 999999999
                || request.getTimestamp().getSeconds() < -62135596800L
                || request.getTimestamp().getSeconds() > 253402300799L) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Invalid action, ids or timestamp").asRuntimeException());
            return;
        }
        try {
            ActionTypeAvro type = switch (request.getActionType()) {
                case ACTION_VIEW -> ActionTypeAvro.VIEW;
                case ACTION_REGISTER -> ActionTypeAvro.REGISTER;
                case ACTION_LIKE -> ActionTypeAvro.LIKE;
                default -> throw new IllegalArgumentException("Unknown action");
            };
            var action = UserActionAvro.newBuilder()
                    .setUserId(request.getUserId()).setEventId(request.getEventId())
                    .setActionType(type)
                    .setTimestamp(Instant.ofEpochSecond(request.getTimestamp().getSeconds(), request.getTimestamp().getNanos()))
                    .build();
            // Acknowledge gRPC only after the broker acknowledges the Avro record.
            kafka.send(topic, action.getUserId(), action).get(10, TimeUnit.SECONDS);
            observer.onNext(Empty.getDefaultInstance());
            observer.onCompleted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            observer.onError(Status.UNAVAILABLE.withDescription("Action delivery interrupted").withCause(e).asRuntimeException());
        } catch (Exception e) {
            observer.onError(Status.UNAVAILABLE.withDescription("Cannot persist action in Kafka").withCause(e).asRuntimeException());
        }
    }
}
