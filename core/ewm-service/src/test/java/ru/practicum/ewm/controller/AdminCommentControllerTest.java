package ru.practicum.ewm.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.controller.admin.AdminCommentController;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.service.CommentService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCommentController.class)
class AdminCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;

    @Test
    void getAllComments_shouldReturnComments() throws Exception {
        Mockito.when(commentService.getAllComments(eq("PENDING"), eq(0), eq(10)))
                .thenReturn(List.of(commentDto("PENDING")));

        mockMvc.perform(get("/admin/comments")
                        .param("status", "PENDING")
                        .param("from", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].text").value("Test comment"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAllComments_withoutStatus_shouldReturnComments() throws Exception {
        Mockito.when(commentService.getAllComments(eq(null), eq(0), eq(10)))
                .thenReturn(List.of(commentDto("PENDING")));

        mockMvc.perform(get("/admin/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void publishComment_shouldReturnPublishedComment() throws Exception {
        Mockito.when(commentService.publishComment(10L))
                .thenReturn(commentDto("PUBLISHED"));

        mockMvc.perform(patch("/admin/comments/{commentId}/publish", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void rejectComment_shouldReturnRejectedComment() throws Exception {
        Mockito.when(commentService.rejectComment(10L))
                .thenReturn(commentDto("REJECTED"));

        mockMvc.perform(patch("/admin/comments/{commentId}/reject", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void deleteComment_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/admin/comments/{commentId}", 10L))
                .andExpect(status().isNoContent());

        Mockito.verify(commentService).deleteCommentByAdmin(10L);
    }

    @Test
    void getAllComments_withInvalidSize_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/admin/comments")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    private CommentDto commentDto(String status) {
        return CommentDto.builder()
                .id(10L)
                .text("Test comment")
                .created(LocalDateTime.of(2026, 6, 2, 12, 0))
                .updated(null)
                .status(status)
                .eventId(2L)
                .author(UserShortDto.builder()
                        .id(1L)
                        .name("User")
                        .build())
                .build();
    }
}
