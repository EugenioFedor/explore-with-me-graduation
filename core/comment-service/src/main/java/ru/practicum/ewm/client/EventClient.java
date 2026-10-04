package ru.practicum.ewm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.ewm.dto.EventForCommentDto;

@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/internal/events/{eventId}/comment-info")
    EventForCommentDto getEvent(@PathVariable("eventId") Long eventId);
}
