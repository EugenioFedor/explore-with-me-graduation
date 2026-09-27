package ru.practicum.stats.client;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class StatsClient {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ParameterizedTypeReference<List<ViewStatsDto>> STATS_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final DiscoveryClient discoveryClient;
    private final RestClient.Builder restClientBuilder;

    public StatsClient(DiscoveryClient discoveryClient,
                       RestClient.Builder restClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.restClientBuilder = restClientBuilder;
    }

    public void hit(EndpointHitDto hit) {
        getRestClient().post()
                .uri("/hit")
                .body(hit)
                .retrieve()
                .toBodilessEntity();
    }

    public List<ViewStatsDto> getStats(LocalDateTime start,
                                       LocalDateTime end,
                                       List<String> uris,
                                       boolean unique) {
        List<ViewStatsDto> body = getRestClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stats")
                        .queryParam("start", start.format(FORMATTER))
                        .queryParam("end", end.format(FORMATTER))
                        .queryParam("unique", unique)
                        .queryParamIfPresent("uris",
                                uris == null || uris.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(uris))
                        .build())
                .retrieve()
                .body(STATS_LIST);

        return body == null ? Collections.emptyList() : body;
    }

    private RestClient getRestClient() {
        List<ServiceInstance> instances =
                discoveryClient.getInstances("stats-server");

        if (instances.isEmpty()) {
            throw new IllegalStateException(
                    "No instances of stats-server found"
            );
        }

        ServiceInstance instance = instances.getFirst();

        return restClientBuilder
                .baseUrl(instance.getUri().toString())
                .build();
    }
}
