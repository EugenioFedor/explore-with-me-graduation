package ru.practicum.ewm.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.EventForRequestDto;
@FeignClient(name = "event-service")
public interface EventClient {
    @GetMapping("/internal/events/{eventId}/request-info") EventForRequestDto getEvent(@PathVariable("eventId") Long eventId);
}
