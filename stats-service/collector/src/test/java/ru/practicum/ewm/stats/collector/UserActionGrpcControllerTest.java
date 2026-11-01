package ru.practicum.ewm.stats.collector;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserActionGrpcControllerTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Long, SpecificRecordBase> kafka = mock(KafkaTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamObserver<Empty> observer = mock(StreamObserver.class);
    private final UserActionGrpcController controller = new UserActionGrpcController(kafka);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "topic", "actions");
    }

    private UserActionProto valid() {
        return UserActionProto.newBuilder().setUserId(1).setEventId(2).setActionType(ActionTypeProto.ACTION_REGISTER)
                .setTimestamp(Timestamp.newBuilder().setSeconds(123).setNanos(456000000)).build();
    }

    @Test
    void mapsActionAndAcknowledgesOnlyAfterKafkaSuccess() {
        when(kafka.send(eq("actions"), eq(1L), any(SpecificRecordBase.class))).thenAnswer(call -> {
            UserActionAvro action = call.getArgument(2);
            assertThat(action.getUserId()).isEqualTo(1);
            assertThat(action.getEventId()).isEqualTo(2);
            assertThat(action.getActionType()).isEqualTo(ActionTypeAvro.REGISTER);
            assertThat(action.getTimestamp().toEpochMilli()).isEqualTo(123456);
            verifyNoInteractions(observer);
            return CompletableFuture.completedFuture(null);
        });
        controller.collectUserAction(valid(), observer);
        verify(observer).onNext(Empty.getDefaultInstance());
        verify(observer).onCompleted();
    }

    @Test
    void brokerFailureReturnsUnavailableWithoutSuccessfulAcknowledgement() {
        when(kafka.send(anyString(), anyLong(), any(SpecificRecordBase.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        controller.collectUserAction(valid(), observer);
        verify(observer).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.UNAVAILABLE));
        verify(observer, never()).onCompleted();
        verify(observer, never()).onNext(any());
    }

    @Test
    void missingTimestampAndInvalidUserAreRejectedBeforePublishing() {
        controller.collectUserAction(UserActionProto.newBuilder().setEventId(2).build(), observer);
        verify(observer).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.INVALID_ARGUMENT));
        verifyNoInteractions(kafka);
    }

    @Test
    void unknownEnumAndInvalidTimestampAreRejected() {
        controller.collectUserAction(valid().toBuilder().setActionTypeValue(99).build(), observer);
        controller.collectUserAction(valid().toBuilder().setTimestamp(Timestamp.newBuilder().setNanos(-1)).build(), observer);
        verify(observer, times(2)).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.INVALID_ARGUMENT));
        verifyNoInteractions(kafka);
    }
}
