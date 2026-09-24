package ru.practicum.ewm.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.*;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.*;
import ru.practicum.ewm.repository.*;
import ru.practicum.ewm.service.EventService;
import ru.practicum.ewm.service.StatsHelperService;
import ru.practicum.ewm.specification.EventSpecification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final long MIN_HOURS_BEFORE_EVENT = 2L;

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final CommentRepository commentRepository;
    private final EventMapper eventMapper;
    private final StatsHelperService statsHelperService;

    @Override
    public List<EventShortDto> getPublicEvents(PublicEventSearchParams params) {
        LocalDateTime start = parseDate(params.getRangeStart());
        LocalDateTime end = parseDate(params.getRangeEnd());

        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "Field: rangeEnd. Error: rangeEnd должен быть позже rangeStart."
            );
        }

        if (start == null && end == null) {
            start = LocalDateTime.now();
        }

        Pageable pageable = PageRequest.of(
                params.getFrom() / params.getSize(),
                params.getSize(),
                Sort.by(Sort.Direction.ASC, "eventDate")
        );

        Specification<Event> specification = EventSpecification.publicFilter(
                normalizeText(params.getText()),
                emptyToNull(params.getCategories()),
                params.getPaid(),
                start,
                end
        );

        List<Event> events = eventRepository.findAll(specification, pageable).getContent();

        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);
        Map<Long, Long> commentsCount = getCommentsCount(events);

        if (Boolean.TRUE.equals(params.getOnlyAvailable())) {
            events = events.stream()
                    .filter(event -> isAvailable(event,
                            confirmedRequests.getOrDefault(event.getId(), 0L)))
                    .toList();
        }

        Map<Long, Long> views = statsHelperService.getViews(events);
        statsHelperService.hit(params.getRequest());

        List<EventShortDto> result = events.stream()
                .map(event -> toShortDto(event,
                        views.getOrDefault(event.getId(), 0L),
                        commentsCount.getOrDefault(event.getId(), 0L)))
                .toList();

        if ("VIEWS".equalsIgnoreCase(params.getSort())) {
            return result.stream()
                    .sorted(Comparator.comparing(EventShortDto::getViews).reversed())
                    .toList();
        }

        return result;
    }

    @Override
    public EventFullDto getPublicEvent(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        statsHelperService.hit(request);

        long views = statsHelperService.getViews(event);
        long commentsCount = commentRepository.countByEventIdAndStatus(eventId, CommentStatus.PUBLISHED);

        return toFullDto(event, views, commentsCount);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        checkUserExists(userId);

        Pageable pageable = PageRequest.of(from / size, size);

        List<Event> events = eventRepository
                .findByInitiatorId(userId, pageable)
                .getContent();

        Map<Long, Long> views = statsHelperService.getViews(events);
        Map<Long, Long> commentsCount = getCommentsCount(events);

        return events.stream()
                .map(event ->
                        toShortDto(event, views.getOrDefault(event.getId(), 0L),
                                commentsCount.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    @Override
    public EventFullDto addEvent(Long userId, NewEventDto newEventDto) {
        User initiator = getUser(userId);
        Category category = getCategory(newEventDto.getCategory());

        if (newEventDto.getEventDate()
                .isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new IllegalArgumentException(
                    "Field: eventDate. Error: должно содержать дату, которая еще не наступила."
            );
        }

        Event event = eventMapper.toEntity(newEventDto);

        event.setCategory(category);
        event.setInitiator(initiator);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());

        event = eventRepository.save(event);

        return toFullDto(event, 0L, 0L);
    }

    @Override
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        checkUserExists(userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " was not found"));

        long views = statsHelperService.getViews(event);
        long commentsCount = commentRepository.countByEventIdAndStatus(eventId, CommentStatus.PUBLISHED);

        return toFullDto(event, views, commentsCount);
    }

    @Override
    public EventFullDto updateEventByUser(
            Long userId,
            Long eventId,
            UpdateEventUserRequest updateRequest
    ) {
        checkUserExists(userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException(
                    "Only pending or canceled events can be changed"
            );
        }

        if (updateRequest.getEventDate() != null
                && updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new IllegalArgumentException(
                    "Field: eventDate. Error: должно содержать дату, которая еще не наступила."
            );
        }

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case SEND_TO_REVIEW -> event.setState(EventState.PENDING);
                case CANCEL_REVIEW -> event.setState(EventState.CANCELED);
            }
        }

        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            event.setCategory(getCategory(updateRequest.getCategory()));
        }
        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            if (event.getLocation() == null) {
                event.setLocation(new Location());
            }
            event.getLocation().setLat(updateRequest.getLocation().getLat());
            event.getLocation().setLon(updateRequest.getLocation().getLon());
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
        }

        event = eventRepository.save(event);

        long views = statsHelperService.getViews(event);
        long commentsCount = commentRepository.countByEventIdAndStatus(eventId, CommentStatus.PUBLISHED);

        return toFullDto(event, views, commentsCount);
    }

    @Override
    public List<EventFullDto> searchEventsByAdmin(AdminEventSearchParams params) {
        List<EventState> eventStates = null;

        if (params.getStates() != null) {
            eventStates = params.getStates().stream()
                    .map(EventState::valueOf)
                    .toList();
        }

        int from = params.getFrom() < 0 ? 0 : params.getFrom();
        int size = params.getSize() <= 0 ? 10 : params.getSize();

        Pageable pageable = PageRequest.of(
                from / size,
                size,
                Sort.by(Sort.Direction.ASC, "eventDate")
        );

        Specification<Event> specification = EventSpecification.adminFilter(
                params.getUsers(),
                eventStates,
                params.getCategories(),
                params.getRangeStart(),
                params.getRangeEnd()
        );

        List<Event> events = eventRepository.findAll(specification, pageable).getContent();

        Map<Long, Long> views = statsHelperService.getViews(events);
        Map<Long, Long> commentsCount = getCommentsCount(events);

        return events.stream()
                .map(event -> toFullDto(event,
                        views.getOrDefault(event.getId(), 0L),
                        commentsCount.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    @Override
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getCategory() != null) {
            event.setCategory(getCategory(updateRequest.getCategory()));
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getLocation() != null) {
            if (event.getLocation() == null) {
                event.setLocation(new Location());
            }
            event.getLocation().setLat(updateRequest.getLocation().getLat());
            event.getLocation().setLon(updateRequest.getLocation().getLon());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }

        if (updateRequest.getEventDate() != null
                && updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new IllegalArgumentException(
                    "Field: eventDate. Error: должно содержать дату, которая еще не наступила."
            );
        }

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case PUBLISH_EVENT -> {
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                }
                case REJECT_EVENT -> {
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Cannot reject the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(EventState.CANCELED);
                }
            }
        }

        event = eventRepository.save(event);

        long views = statsHelperService.getViews(event);
        long commentsCount = commentRepository.countByEventIdAndStatus(eventId, CommentStatus.PUBLISHED);

        return toFullDto(event, views, commentsCount);
    }

    private EventShortDto toShortDto(Event event, long views, long commentsCount) {
        EventShortDto dto = eventMapper.toShortDto(event);

        dto.setConfirmedRequests(
                requestRepository.countByEventIdAndStatus(
                        event.getId(),
                        RequestStatus.CONFIRMED
                )
        );

        dto.setViews(views);
        dto.setComments(commentsCount);

        return dto;
    }

    private EventFullDto toFullDto(Event event, long views, long commentsCount) {
        EventFullDto dto = eventMapper.toFullDto(event);

        dto.setConfirmedRequests(
                requestRepository.countByEventIdAndStatus(
                        event.getId(),
                        RequestStatus.CONFIRMED
                )
        );

        dto.setViews(views);
        dto.setComments(commentsCount);

        return dto;
    }

    private Map<Long, Long> getConfirmedRequests(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        return requestRepository.countByEventIdsAndStatus(
                        eventIds,
                        RequestStatus.CONFIRMED
                )
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    private Map<Long, Long> getCommentsCount(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        return commentRepository.countByEventIdsAndStatus(eventIds, CommentStatus.PUBLISHED);
    }

    private boolean isAvailable(Event event, Long confirmedRequests) {
        Integer limit = event.getParticipantLimit();
        return limit == null
                || limit == 0
                || confirmedRequests < limit;
    }

    private LocalDateTime parseDate(String value) {
        return value == null || value.isBlank()
                ? null
                : LocalDateTime.parse(value, FORMATTER);
    }

    private String normalizeText(String text) {
        return text == null || text.isBlank()
                ? null
                : text;
    }

    private List<Long> emptyToNull(List<Long> values) {
        return values == null || values.isEmpty()
                ? null
                : values;
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new NotFoundException("Category with id=" + categoryId + " was not found"));
    }
}