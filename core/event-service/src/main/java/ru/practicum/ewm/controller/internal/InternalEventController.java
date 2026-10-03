package ru.practicum.ewm.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.EventForRequestDto;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.repository.EventRepository;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class InternalEventController {
    private final EventRepository eventRepository;

    @GetMapping("/{eventId}/request-info")
    public EventForRequestDto getRequestInfo(@PathVariable Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return EventForRequestDto.builder()
                .id(event.getId())
                .initiatorId(event.getInitiatorId())
                .state(event.getState())
                .participantLimit(event.getParticipantLimit())
                .requestModeration(event.getRequestModeration())
                .build();
    }
    @GetMapping("/{eventId}/comment-info")
    public ru.practicum.ewm.dto.EventForCommentDto getCommentInfo(@PathVariable Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return ru.practicum.ewm.dto.EventForCommentDto.builder()
                .id(event.getId())
                .state(event.getState().name())
                .build();
    }

}
