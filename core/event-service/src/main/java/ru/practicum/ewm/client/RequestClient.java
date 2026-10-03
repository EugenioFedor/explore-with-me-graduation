package ru.practicum.ewm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Collection;
import java.util.Map;

@FeignClient(name = "request-service")
public interface RequestClient {
    @GetMapping("/internal/requests/events/{eventId}/confirmed-count")
    long getConfirmedCount(@PathVariable("eventId") Long eventId);

    @PostMapping("/internal/requests/events/confirmed-counts")
    Map<Long, Long> getConfirmedCounts(@RequestBody Collection<Long> eventIds);
}
