package ru.practicum.ewm.controller.publicapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.exception.ErrorHandler;
import ru.practicum.ewm.service.EventService;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecommendationEndpointsTest {
    private final EventService service = mock(EventService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PublicEventController(service))
                .setControllerAdvice(new ErrorHandler()).build();
    }

    @Test
    void eventCardRequiresUserHeaderAndHasRatingInsteadOfViews() throws Exception {
        var event = EventFullDto.builder().id(2L).rating(1.8).build();
        when(service.getPublicEvent(2L, 1L)).thenReturn(event);
        mvc.perform(get("/events/2").header("X-EWM-USER-ID", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(1.8))
                .andExpect(jsonPath("$.views").doesNotExist());
        mvc.perform(get("/events/2")).andExpect(status().isBadRequest());
    }

    @Test
    void recommendationsUseSpecificRouteRatherThanTreatingItAsEventId() throws Exception {
        when(service.getRecommendations(1, 3)).thenReturn(List.of(EventShortDto.builder().id(2L).rating(0.8).build()));
        mvc.perform(get("/events/recommendations").header("X-EWM-USER-ID", 1).param("size", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(2));
        verify(service).getRecommendations(1, 3);
    }

    @Test
    void deniedLikeReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("not a participant")).when(service).likeEvent(1, 2);
        mvc.perform(put("/events/2/like").header("X-EWM-USER-ID", 1)).andExpect(status().isBadRequest());
    }

    @Test
    void acceptedLikeReturnsNoContent() throws Exception {
        mvc.perform(put("/events/2/like").header("X-EWM-USER-ID", 1)).andExpect(status().isNoContent());
        verify(service).likeEvent(1, 2);
    }
}
