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
import ru.practicum.ewm.client.UserClient;
import ru.practicum.ewm.client.EventClient;
import ru.practicum.ewm.dto.EventForCommentDto;
import ru.practicum.ewm.dto.UserDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.service.CommentService;
import ru.practicum.ewm.service.StatsHelperService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final CommentMapper commentMapper;
    private final StatsHelperService statsHelperService;

    @Override
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        checkUserExists(userId);
        EventForCommentDto event = getEvent(eventId);

        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException("Only published events can be commented");
        }

        Comment comment = commentMapper.toEntity(newCommentDto);
        comment.setAuthorId(userId);
        comment.setEventId(eventId);
        comment.setStatus(CommentStatus.PENDING);
        comment.setCreated(LocalDateTime.now());

        Comment savedComment = commentRepository.save(comment);

        return toDto(savedComment);
    }

    @Override
    public List<CommentDto> getUserComments(Long userId, int from, int size) {
        checkUserExists(userId);

        Pageable pageable = PageRequest.of(from / size, size);

        return commentRepository.findByAuthorId(userId, pageable)
                .getContent()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto) {
        checkUserExists(userId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (!comment.getAuthorId().equals(userId)) {
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

        return toDto(updatedComment);
    }

    @Override
    public void deleteComment(Long userId, Long commentId) {
        checkUserExists(userId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (!comment.getAuthorId().equals(userId)) {
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
                .map(this::toDto)
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

        return toDto(comment);
    }

    @Override
    public List<CommentDto> getAllComments(String status, int from, int size) {
        // Если статус не указан — возвращаем комментарии всех статусов
        CommentStatus commentStatus = CommentStatus.from(status);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.DESC, "created"));

        return commentRepository.findAllByStatus(commentStatus, pageable)
                .getContent()
                .stream()
                .map(this::toDto)
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

        return toDto(commentRepository.save(comment));
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

        return toDto(commentRepository.save(comment));
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

    private EventForCommentDto getEvent(Long eventId) {
        return eventClient.getEvent(eventId);
    }

    private void checkUserExists(Long userId) {
        userClient.getUser(userId);
    }

    private void checkEventExists(Long eventId) {
        eventClient.getEvent(eventId);
    }

    private CommentDto toDto(Comment comment) {
        CommentDto dto = commentMapper.toDto(comment);

        UserDto user = userClient.getUser(comment.getAuthorId());

        dto.setAuthor(UserShortDto.builder()
                .id(user.getId())
                .name(user.getName())
                .build());

        return dto;
    }
}