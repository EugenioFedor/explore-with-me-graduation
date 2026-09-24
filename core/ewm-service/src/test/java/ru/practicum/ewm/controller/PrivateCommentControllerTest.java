package ru.practicum.ewm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.controller.priv.PrivateCommentController;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.service.CommentService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrivateCommentController.class)
class PrivateCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommentService commentService;

    @Test
    void addComment_shouldReturnCreatedComment() throws Exception {
        NewCommentDto request = NewCommentDto.builder()
                .text("Test comment")
                .build();

        CommentDto response = commentDto();

        Mockito.when(commentService.addComment(eq(1L), eq(2L), any(NewCommentDto.class)))
                .thenReturn(response);

        mockMvc.perform(post("/users/{userId}/events/{eventId}/comments", 1L, 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.text").value("Test comment"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.eventId").value(2))
                .andExpect(jsonPath("$.author.id").value(1));
    }

    @Test
    void getUserComments_shouldReturnComments() throws Exception {
        Mockito.when(commentService.getUserComments(1L, 0, 10))
                .thenReturn(List.of(commentDto()));

        mockMvc.perform(get("/users/{userId}/comments", 1L)
                        .param("from", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].text").value("Test comment"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void updateComment_shouldReturnUpdatedComment() throws Exception {
        UpdateCommentDto request = UpdateCommentDto.builder()
                .text("Updated comment")
                .build();

        CommentDto response = CommentDto.builder()
                .id(10L)
                .text("Updated comment")
                .created(LocalDateTime.of(2026, 6, 2, 12, 0))
                .updated(LocalDateTime.of(2026, 6, 2, 13, 0))
                .status("PENDING")
                .eventId(2L)
                .author(UserShortDto.builder()
                        .id(1L)
                        .name("User")
                        .build())
                .build();

        Mockito.when(commentService.updateComment(eq(1L), eq(10L), any(UpdateCommentDto.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/users/{userId}/comments/{commentId}", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.text").value("Updated comment"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void deleteComment_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/users/{userId}/comments/{commentId}", 1L, 10L))
                .andExpect(status().isNoContent());

        Mockito.verify(commentService).deleteComment(1L, 10L);
    }

    @Test
    void addComment_withBlankText_shouldReturnBadRequest() throws Exception {
        NewCommentDto request = NewCommentDto.builder()
                .text("")
                .build();

        mockMvc.perform(post("/users/{userId}/events/{eventId}/comments", 1L, 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private CommentDto commentDto() {
        return CommentDto.builder()
                .id(10L)
                .text("Test comment")
                .created(LocalDateTime.of(2026, 6, 2, 12, 0))
                .updated(null)
                .status("PENDING")
                .eventId(2L)
                .author(UserShortDto.builder()
                        .id(1L)
                        .name("User")
                        .build())
                .build();
    }
}