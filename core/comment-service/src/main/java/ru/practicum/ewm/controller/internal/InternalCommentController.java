package ru.practicum.ewm.controller.internal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.model.CommentStatus;
import ru.practicum.ewm.repository.CommentRepository;
import java.util.*;
@RestController @RequestMapping("/internal/comments") @RequiredArgsConstructor
public class InternalCommentController {
    private final CommentRepository commentRepository;
    @GetMapping("/events/{eventId}/published-count")
    public long getPublishedCount(@PathVariable Long eventId) { return commentRepository.countByEventIdAndStatus(eventId, CommentStatus.PUBLISHED); }
    @PostMapping("/events/published-counts")
    public Map<Long,Long> getPublishedCounts(@RequestBody Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Map.of();
        return commentRepository.countByEventIdsAndStatus(eventIds, CommentStatus.PUBLISHED).stream()
                .collect(java.util.stream.Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
