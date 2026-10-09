package ru.practicum.ewm.stats.analyzer;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendationsControllerGrpc;
import java.util.List;
import java.util.function.Supplier;

@GrpcService
@RequiredArgsConstructor
public class RecommendationsGrpcController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final StatsRepository repository;
    private final RecommendationEngine engine;
    @Value("${stats.recommendations.recent-limit:100}")
    private int recentLimit;
    @Value("${stats.recommendations.neighbors-limit:10}")
    private int neighborsLimit;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request, StreamObserver<RecommendedEventProto> observer) {
        respond(observer, () -> {
            validate(request.getUserId(), request.getMaxResults());
            List<Interaction> history = repository.history(request.getUserId());
            var pairs = repository.similarities(history.stream().map(Interaction::eventId).toList());
            return engine.predict(history, pairs, request.getMaxResults(), recentLimit, neighborsLimit);
        });
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request, StreamObserver<RecommendedEventProto> observer) {
        respond(observer, () -> {
            validate(request.getUserId(), request.getMaxResults());
            if (request.getEventId() <= 0) {
                throw new IllegalArgumentException("event_id must be positive");
            }
            return engine.similar(request.getEventId(), repository.history(request.getUserId()),
                    repository.similarities(List.of(request.getEventId())), request.getMaxResults());
        });
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request, StreamObserver<RecommendedEventProto> observer) {
        respond(observer, () -> {
            if (request.getEventIdList().stream().anyMatch(id -> id <= 0)) {
                throw new IllegalArgumentException("event ids must be positive");
            }
            return repository.interactions(request.getEventIdList());
        });
    }

    private void validate(long userId, int maxResults) {
        if (userId <= 0 || maxResults < 0) {
            throw new IllegalArgumentException("user_id must be positive and max_results nonnegative");
        }
    }

    private void respond(StreamObserver<RecommendedEventProto> observer, Supplier<List<Recommendation>> operation) {
        try {
            for (Recommendation item : operation.get()) {
                observer.onNext(RecommendedEventProto.newBuilder().setEventId(item.eventId()).setScore(item.score()).build());
            }
            observer.onCompleted();
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            observer.onError(Status.UNAVAILABLE.withDescription("Cannot query recommendations").withCause(e).asRuntimeException());
        }
    }
}
