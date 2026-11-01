package ru.practicum.ewm.controller.publicapi;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.PublicEventSearchParams;
import ru.practicum.ewm.service.EventService;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/events")
public class PublicEventController {

    private final EventService eventService;

    @GetMapping
    public List<EventShortDto> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) String rangeStart,
            @RequestParam(required = false) String rangeEnd,
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size,
            HttpServletRequest request
    ) {
        PublicEventSearchParams params = PublicEventSearchParams.builder()
                .text(text)
                .categories(categories)
                .paid(paid)
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .onlyAvailable(onlyAvailable)
                .sort(sort)
                .from(from)
                .size(size)
                .request(request)
                .build();
        return eventService.getPublicEvents(params);
    }

    @GetMapping("/{id}")
    public EventFullDto getEvent(@PathVariable @Positive Long id,
                                 @RequestHeader("X-EWM-USER-ID") @Positive long userId) {
        return eventService.getPublicEvent(id, userId);
    }

    @GetMapping("/recommendations")
    public List<EventShortDto> getRecommendations(@RequestHeader("X-EWM-USER-ID") @Positive long userId,
                                                  @RequestParam(defaultValue = "10") @Positive int size) {
        return eventService.getRecommendations(userId, size);
    }

    @PutMapping("/{eventId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@PathVariable @Positive long eventId,
                     @RequestHeader("X-EWM-USER-ID") @Positive long userId) {
        eventService.likeEvent(userId, eventId);
    }
}
