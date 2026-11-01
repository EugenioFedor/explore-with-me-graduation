package ru.practicum.stats.client;

import com.google.protobuf.Timestamp;
import net.devh.boot.grpc.client.inject.GrpcClient;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.ewm.stats.proto.collector.UserActionControllerGrpc;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class CollectorClient {
    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public void collect(long userId, long eventId, ActionTypeProto type) {
        Instant now = Instant.now();
        var request = UserActionProto.newBuilder().setUserId(userId).setEventId(eventId).setActionType(type)
                .setTimestamp(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano())).build();
        client.withDeadlineAfter(12, TimeUnit.SECONDS).collectUserAction(request);
    }
}
