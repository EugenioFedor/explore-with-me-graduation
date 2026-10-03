package ru.practicum.ewm.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@FeignClient(name="comment-service")
public interface CommentClient {
    @GetMapping("/internal/comments/events/{eventId}/published-count") long getPublishedCount(@PathVariable("eventId") Long eventId);
    @PostMapping("/internal/comments/events/published-counts") Map<Long,Long> getPublishedCounts(@RequestBody Collection<Long> eventIds);
}
