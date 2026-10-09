package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.model.Event;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsHelperService {

    private static final String URI_PREFIX = "/events/";
    private static final LocalDateTime DEFAULT_START = LocalDateTime.of(2000, 1, 1, 0, 0, 0);

    private final StatsClient statsClient;
    private final String appName;

    public Map<Long, Long> getViews(Collection<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        try {
            List<String> uris = events.stream()
                    .map(event -> URI_PREFIX + event.getId())
                    .toList();

            LocalDateTime start = events.stream()
                    .map(Event::getCreatedOn)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(DEFAULT_START);

            List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, true);

            Map<String, Long> hitsByUri = stats.stream()
                    .collect(Collectors.toMap(ViewStatsDto::uri, ViewStatsDto::hits, (first, second) -> first));

            return events.stream()
                    .collect(Collectors.toMap(
                            Event::getId,
                            event -> hitsByUri.getOrDefault(URI_PREFIX + event.getId(), 0L),
                            (first, second) -> first,
                            HashMap::new));
        } catch (Exception e) {
            log.warn("Cannot get stats views", e);
            return events.stream()
                    .collect(Collectors.toMap(
                            Event::getId,
                            event -> 0L,
                            (first, second) -> first,
                            HashMap::new));
        }
    }

    public long getViews(Event event) {
        return getViews(List.of(event)).getOrDefault(event.getId(), 0L);
    }

    public void hit(HttpServletRequest request) {
        try {
            statsClient.hit(new EndpointHitDto(
                    appName,
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.warn("Cannot save stats hit for uri={}", request.getRequestURI(), e);
        }
    }
}