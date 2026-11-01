package ru.practicum.stats.client;

import net.devh.boot.grpc.client.inject.GrpcClient;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendationsControllerGrpc;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

public class AnalyzerClient {
    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    public List<RecommendedEventProto> recommendations(long userId, int maxResults) {
        var request = UserPredictionsRequestProto.newBuilder().setUserId(userId).setMaxResults(maxResults).build();
        return collect(client.withDeadlineAfter(5, TimeUnit.SECONDS).getRecommendationsForUser(request));
    }

    public List<RecommendedEventProto> similar(long eventId, long userId, int maxResults) {
        var request = SimilarEventsRequestProto.newBuilder().setEventId(eventId).setUserId(userId).setMaxResults(maxResults).build();
        return collect(client.withDeadlineAfter(5, TimeUnit.SECONDS).getSimilarEvents(request));
    }

    public List<RecommendedEventProto> interactions(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        var request = InteractionsCountRequestProto.newBuilder().addAllEventId(eventIds).build();
        return collect(client.withDeadlineAfter(5, TimeUnit.SECONDS).getInteractionsCount(request));
    }

    private List<RecommendedEventProto> collect(Iterator<RecommendedEventProto> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false).toList();
    }
}
