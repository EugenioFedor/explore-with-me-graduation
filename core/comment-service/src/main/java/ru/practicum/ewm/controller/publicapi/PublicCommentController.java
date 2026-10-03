package ru.practicum.ewm.controller.publicapi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.service.CommentService;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/events/{eventId}/comments")
public class PublicCommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentDto> getEventComments(@PathVariable Long eventId,
                                             @RequestParam(defaultValue = "0") @PositiveOrZero int from,
                                             @RequestParam(defaultValue = "10") @Positive int size,
                                             HttpServletRequest request) {
        log.info("GET /events/{}/comments - from={}, size={}", eventId, from, size);
        return commentService.getEventComments(eventId, from, size, request);
    }

    @GetMapping("/{commentId}")
    public CommentDto getEventComment(@PathVariable Long eventId,
                                      @PathVariable Long commentId,
                                      HttpServletRequest request) {
        log.info("GET /events/{}/comments/{}", eventId, commentId);
        return commentService.getEventComment(eventId, commentId, request);
    }
}
