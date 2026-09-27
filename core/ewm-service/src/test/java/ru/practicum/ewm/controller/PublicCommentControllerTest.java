package ru.practicum.ewm.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.controller.publicapi.PublicCommentController;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.service.CommentService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCommentController.class)
class PublicCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;

    @Test
    void getEventComments_shouldReturnPublishedComments() throws Exception {
        Mockito.when(commentService.getEventComments(eq(2L), eq(0), eq(10), any(HttpServletRequest.class)))
                .thenReturn(List.of(commentDto()));

        mockMvc.perform(get("/events/{eventId}/comments", 2L)
                        .param("from", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].text").value("Test comment"))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void getEventComment_shouldReturnComment() throws Exception {
        Mockito.when(commentService.getEventComment(eq(2L), eq(10L), any(HttpServletRequest.class)))
                .thenReturn(commentDto());

        mockMvc.perform(get("/events/{eventId}/comments/{commentId}", 2L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.text").value("Test comment"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.eventId").value(2));
    }

    @Test
    void getEventComments_withInvalidSize_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/events/{eventId}/comments", 2L)
                        .param("from", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    private CommentDto commentDto() {
        return CommentDto.builder()
                .id(10L)
                .text("Test comment")
                .created(LocalDateTime.of(2026, 6, 2, 12, 0))
                .updated(null)
                .status("PUBLISHED")
                .eventId(2L)
                .author(UserShortDto.builder()
                        .id(1L)
                        .name("User")
                        .build())
                .build();
    }
}
