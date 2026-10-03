package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;

import java.util.List;

public interface CommentService {

    CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto);

    List<CommentDto> getUserComments(Long userId, int from, int size);

    CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto);

    void deleteComment(Long userId, Long commentId);

    List<CommentDto> getEventComments(Long eventId, int from, int size, HttpServletRequest request);

    CommentDto getEventComment(Long eventId, Long commentId, HttpServletRequest request);

    // Административные методы

    List<CommentDto> getAllComments(String status, int from, int size);

    CommentDto publishComment(Long commentId);

    CommentDto rejectComment(Long commentId);

    void deleteCommentByAdmin(Long commentId);
}
