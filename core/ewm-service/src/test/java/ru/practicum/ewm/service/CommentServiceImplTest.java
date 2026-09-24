package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CommentMapper;
import ru.practicum.ewm.model.Comment;
import ru.practicum.ewm.model.CommentStatus;
import ru.practicum.ewm.repository.CommentRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.UserRepository;
import ru.practicum.ewm.service.impl.CommentServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private StatsHelperService statsHelperService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    void getEventComments_shouldReturnPublishedComments() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.PUBLISHED);

        CommentDto dto = CommentDto.builder().id(10L).status("PUBLISHED").build();

        when(eventRepository.existsById(2L)).thenReturn(true);
        when(commentRepository.findByEventIdAndStatus(eq(2L), eq(CommentStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(commentMapper.toDto(comment)).thenReturn(dto);

        List<CommentDto> result = commentService.getEventComments(2L, 0, 10, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        verify(statsHelperService).hit(request);
    }

    @Test
    void getEventComments_whenEventNotFound_shouldThrowNotFound() {
        when(eventRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.getEventComments(99L, 0, 10, request))
                .isInstanceOf(NotFoundException.class);

        verify(statsHelperService, never()).hit(any());
    }

    @Test
    void getEventComment_shouldReturnPublishedComment() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.PUBLISHED);

        CommentDto dto = CommentDto.builder().id(10L).status("PUBLISHED").build();

        when(commentRepository.findByIdAndEventId(10L, 2L)).thenReturn(Optional.of(comment));
        when(commentMapper.toDto(comment)).thenReturn(dto);

        CommentDto result = commentService.getEventComment(2L, 10L, request);

        assertThat(result.getId()).isEqualTo(10L);
        verify(statsHelperService).hit(request);
    }

    @Test
    void getEventComment_whenNotPublished_shouldThrowNotFound() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.PENDING);

        when(commentRepository.findByIdAndEventId(10L, 2L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.getEventComment(2L, 10L, request))
                .isInstanceOf(NotFoundException.class);

        verify(statsHelperService, never()).hit(any());
        verify(commentMapper, never()).toDto(any());
    }

    @Test
    void getEventComment_whenCommentNotFound_shouldThrowNotFound() {
        when(commentRepository.findByIdAndEventId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getEventComment(2L, 10L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getAllComments_shouldFilterByStatus() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.PENDING);

        CommentDto dto = CommentDto.builder().id(10L).status("PENDING").build();

        when(commentRepository.findAllByStatus(eq(CommentStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(commentMapper.toDto(comment)).thenReturn(dto);

        List<CommentDto> result = commentService.getAllComments("PENDING", 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void getAllComments_withoutStatus_shouldUseNullFilter() {
        when(commentRepository.findAllByStatus(eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<CommentDto> result = commentService.getAllComments(null, 0, 10);

        assertThat(result).isEmpty();
        verify(commentRepository).findAllByStatus(eq(null), any(Pageable.class));
    }

    @Test
    void getAllComments_withUnknownStatus_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> commentService.getAllComments("WRONG", 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishComment_shouldSetPublishedStatus() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.PENDING);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentMapper.toDto(any(Comment.class)))
                .thenAnswer(inv -> CommentDto.builder()
                        .id(10L)
                        .status(((Comment) inv.getArgument(0)).getStatus().name())
                        .build());

        CommentDto result = commentService.publishComment(10L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.PUBLISHED);
        assertThat(comment.getUpdated()).isNotNull();
    }

    @Test
    void publishComment_whenNotPending_shouldThrowConflict() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.PUBLISHED);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.publishComment(10L))
                .isInstanceOf(ConflictException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void publishComment_whenNotFound_shouldThrowNotFound() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.publishComment(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectComment_shouldSetRejectedStatus() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.PENDING);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentMapper.toDto(any(Comment.class)))
                .thenAnswer(inv -> CommentDto.builder()
                        .id(10L)
                        .status(((Comment) inv.getArgument(0)).getStatus().name())
                        .build());

        CommentDto result = commentService.rejectComment(10L);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.REJECTED);
    }

    @Test
    void rejectComment_whenNotPending_shouldThrowConflict() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setStatus(CommentStatus.REJECTED);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.rejectComment(10L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteCommentByAdmin_shouldDeleteFromDb() {
        when(commentRepository.existsById(10L)).thenReturn(true);

        commentService.deleteCommentByAdmin(10L);

        verify(commentRepository).deleteById(10L);
    }

    @Test
    void deleteCommentByAdmin_whenNotFound_shouldThrowNotFound() {
        when(commentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.deleteCommentByAdmin(99L))
                .isInstanceOf(NotFoundException.class);

        verify(commentRepository, never()).deleteById(anyLong());
    }
}
