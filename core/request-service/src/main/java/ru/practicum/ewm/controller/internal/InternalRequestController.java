package ru.practicum.ewm.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.model.RequestStatus;
import ru.practicum.ewm.repository.RequestRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class InternalRequestController {
    private final RequestRepository requestRepository;

    @GetMapping("/events/{eventId}/confirmed-count")
    public long getConfirmedCount(@PathVariable Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    @PostMapping("/events/confirmed-counts")
    public Map<Long, Long> getConfirmedCounts(@RequestBody Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Map.of();
        List<Object[]> rows = requestRepository.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED);
        return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @GetMapping("/events/{eventId}/users/{userId}/confirmed")
    public boolean hasConfirmedParticipation(@PathVariable long eventId, @PathVariable long userId) {
        return requestRepository.existsByEventIdAndRequesterIdAndStatus(eventId, userId, RequestStatus.CONFIRMED);
    }
}
