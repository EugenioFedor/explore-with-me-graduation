package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.ewm.dto.*;

import java.util.List;

public interface EventService {

    List<EventShortDto> getPublicEvents(PublicEventSearchParams params);

    EventFullDto getPublicEvent(Long eventId, HttpServletRequest request);

    List<EventShortDto> getUserEvents(Long userId, int from, int size);

    EventFullDto addEvent(Long userId, NewEventDto newEventDto);

    EventFullDto getUserEvent(Long userId, Long eventId);

    EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest);

    List<EventFullDto> searchEventsByAdmin(AdminEventSearchParams params);

    EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest);
}