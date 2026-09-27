package ru.practicum.ewm.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CommentMapper;
import ru.practicum.ewm.model.*;
import ru.practicum.ewm.repository.CommentRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.UserRepository;
import ru.practicum.ewm.service.CommentService;
import ru.practicum.ewm.service.StatsHelperService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final CommentMapper commentMapper;
    private final StatsHelperService statsHelperService;

    @Override
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        User user = getUser(userId);
        Event event = getEvent(eventId);

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Only published events can be commented");
        }

        Comment comment = commentMapper.toEntity(newCommentDto);
        comment.setAuthor(user);
        comment.setEvent(event);
        comment.setStatus(CommentStatus.PENDING);
        comment.setCreated(LocalDateTime.now());

        Comment savedComment = commentRepository.save(comment);

        return commentMapper.toDto(savedComment);
    }

    @Override
    public List<CommentDto> getUserComments(Long userId, int from, int size) {
        checkUserExists(userId);

        Pageable pageable = PageRequest.of(from / size, size);

        return commentRepository.findByAuthorId(userId, pageable)
                .getContent()
                .stream()
                .map(commentMapper::toDto)
                .toList();
    }

    @Override
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto) {
        checkUserExists(userId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }

        if (comment.getStatus() == CommentStatus.PUBLISHED) {
            throw new ConflictException("Published comment cannot be updated");
        }

        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new ConflictException("Deleted comment cannot be updated");
        }

        comment.setText(updateCommentDto.getText());
        comment.setUpdated(LocalDateTime.now());
        comment.setStatus(CommentStatus.PENDING);

        Comment updatedComment = commentRepository.save(comment);

        return commentMapper.toDto(updatedComment);
    }

    @Override
    public void deleteComment(Long userId, Long commentId) {
        checkUserExists(userId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }

        comment.setStatus(CommentStatus.DELETED);
        comment.setUpdated(LocalDateTime.now());

        commentRepository.save(comment);
    }

    @Override
    public List<CommentDto> getEventComments(Long eventId, int from, int size, HttpServletRequest request) {
        checkEventExists(eventId);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.ASC, "created"));

        List<CommentDto> comments = commentRepository
                .findByEventIdAndStatus(eventId, CommentStatus.PUBLISHED, pageable)
                .getContent()
                .stream()
                .map(commentMapper::toDto)
                .toList();

        statsHelperService.hit(request);

        return comments;
    }

    @Override
    public CommentDto getEventComment(Long eventId, Long commentId, HttpServletRequest request) {
        Comment comment = commentRepository.findByIdAndEventId(commentId, eventId)
                .orElseThrow(() ->
                        new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (comment.getStatus() != CommentStatus.PUBLISHED) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }

        statsHelperService.hit(request);

        return commentMapper.toDto(comment);
    }

    @Override
    public List<CommentDto> getAllComments(String status, int from, int size) {
        // Если статус не указан — возвращаем комментарии всех статусов
        CommentStatus commentStatus = CommentStatus.from(status);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.DESC, "created"));

        return commentRepository.findAllByStatus(commentStatus, pageable)
                .getContent()
                .stream()
                .map(commentMapper::toDto)
                .toList();
    }

    @Override
    public CommentDto publishComment(Long commentId) {
        Comment comment = getComment(commentId);

        // Публиковать имеет смысл только комментарий, ожидающий модерации
        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Only comment with status PENDING can be published");
        }

        comment.setStatus(CommentStatus.PUBLISHED);
        comment.setUpdated(LocalDateTime.now());

        return commentMapper.toDto(commentRepository.save(comment));
    }

    @Override
    public CommentDto rejectComment(Long commentId) {
        Comment comment = getComment(commentId);

        // Отклонить можно только комментарий, ожидающий модерации
        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Only comment with status PENDING can be rejected");
        }

        comment.setStatus(CommentStatus.REJECTED);
        comment.setUpdated(LocalDateTime.now());

        return commentMapper.toDto(commentRepository.save(comment));
    }

    @Override
    public void deleteCommentByAdmin(Long commentId) {
        // Администратор удаляет комментарий полностью из БД
        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }
        commentRepository.deleteById(commentId);
    }

    private Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new NotFoundException("Comment with id=" + commentId + " was not found"));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
    }

    private void checkEventExists(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
    }
}