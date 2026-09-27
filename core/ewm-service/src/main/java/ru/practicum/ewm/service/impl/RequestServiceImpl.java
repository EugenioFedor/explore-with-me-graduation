package ru.practicum.ewm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.dto.ParticipationRequestDto;
import ru.practicum.ewm.dto.RequestStatusAction;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.model.*;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.repository.UserRepository;
import ru.practicum.ewm.service.RequestService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RequestMapper requestMapper;

    @Override
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        log.info("User id={} requests participation in event id={}", userId, eventId);
        User requester = getUser(userId);
        Event event = getEvent(eventId);

        // Инициатор не может подать заявку на своё событие
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Event initiator cannot request participation in own event");
        }
        // Нельзя участвовать в неопубликованном событии
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }
        // Нельзя подать заявку повторно
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Request already exists");
        }
        // Проверка лимита участников (0 = без ограничения)
        int limit = event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (limit > 0 && confirmed >= limit) {
            throw new ConflictException("The participant limit has been reached");
        }

        // Если премодерация выключена или лимит не задан — заявка сразу подтверждается
        boolean autoConfirm = limit == 0 || Boolean.FALSE.equals(event.getRequestModeration());

        Request request = Request.builder()
                .created(LocalDateTime.now())
                .event(event)
                .requester(requester)
                .status(autoConfirm ? RequestStatus.CONFIRMED : RequestStatus.PENDING)
                .build();

        return requestMapper.toDto(requestRepository.save(request));
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Getting requests of user id={}", userId);
        checkUserExists(userId);
        return requestMapper.toDtoList(requestRepository.findByRequesterId(userId));
    }

    @Override
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("User id={} cancels request id={}", userId, requestId);
        checkUserExists(userId);
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " was not found"));
        if (!request.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Request with id=" + requestId + " was not found");
        }
        request.setStatus(RequestStatus.CANCELED);
        return requestMapper.toDto(requestRepository.save(request));
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Getting requests for event id={} of user id={}", eventId, userId);
        Event event = getEvent(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        return requestMapper.toDtoList(requestRepository.findByEventId(eventId));
    }

    @Override
    public EventRequestStatusUpdateResult updateRequestsStatus(Long userId,
                                                               Long eventId,
                                                               EventRequestStatusUpdateRequest updateRequest) {
        log.info("User id={} updates requests status for event id={}", userId, eventId);
        Event event = getEvent(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        List<ParticipationRequestDto> confirmedList = new ArrayList<>();
        List<ParticipationRequestDto> rejectedList = new ArrayList<>();

        int limit = event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();
        // Если лимита нет или премодерация выключена — подтверждение не требуется
        if (limit == 0 || Boolean.FALSE.equals(event.getRequestModeration())) {
            return new EventRequestStatusUpdateResult(confirmedList, rejectedList);
        }

        List<Request> requests = requestRepository.findByEventIdAndIdIn(eventId, updateRequest.getRequestIds());
        // Все заявки должны быть в статусе PENDING
        for (Request request : requests) {
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
        }

        // Отклонение — просто переводим все в REJECTED
        if (updateRequest.getStatus() == RequestStatusAction.REJECTED) {
            for (Request request : requests) {
                request.setStatus(RequestStatus.REJECTED);
                rejectedList.add(requestMapper.toDto(request));
            }
            requestRepository.saveAll(requests);
            return new EventRequestStatusUpdateResult(confirmedList, rejectedList);
        }

        // Подтверждение с учётом лимита
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (confirmed >= limit) {
            throw new ConflictException("The participant limit has been reached");
        }

        long available = limit - confirmed;
        for (Request request : requests) {
            if (available > 0) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmedList.add(requestMapper.toDto(request));
                available--;
            } else {
                request.setStatus(RequestStatus.REJECTED);
                rejectedList.add(requestMapper.toDto(request));
            }
        }
        requestRepository.saveAll(requests);

        // Если лимит исчерпан — отклоняем все оставшиеся PENDING-заявки события
        if (available == 0) {
            List<Request> pending = requestRepository.findByEventIdAndStatus(eventId, RequestStatus.PENDING);
            for (Request request : pending) {
                request.setStatus(RequestStatus.REJECTED);
                rejectedList.add(requestMapper.toDto(request));
            }
            requestRepository.saveAll(pending);
        }

        return new EventRequestStatusUpdateResult(confirmedList, rejectedList);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
    }
}
