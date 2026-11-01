package ru.practicum.ewm.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.ewm.client.CommentClient;
import ru.practicum.ewm.client.RequestClient;
import ru.practicum.ewm.client.UserClient;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.EventState;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.service.StatsHelperService;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EventRecommendationsTest {
    private final EventRepository repository = mock(EventRepository.class);
    private final RequestClient requests = mock(RequestClient.class);
    private final StatsHelperService stats = mock(StatsHelperService.class);
    private final EventMapper mapper = mock(EventMapper.class);
    private final EventServiceImpl service = new EventServiceImpl(repository, mock(UserClient.class),
            mock(CategoryRepository.class), requests, mock(CommentClient.class), mapper, stats);
    private Event event;

    @BeforeEach
    void setUp() {
        event = Event.builder().id(10L).initiatorId(2L).state(EventState.PUBLISHED).build();
        when(repository.findById(10L)).thenReturn(Optional.of(event));
    }

    @Test
    void readingPublishedEventSendsUserViewAndReturnsDoubleRating() {
        when(mapper.toFullDto(event)).thenReturn(new EventFullDto());
        when(stats.getRating(event)).thenReturn(1.8);
        assertThat(service.getPublicEvent(10L, 1L).getRating()).isEqualTo(1.8);
        verify(stats).view(1L, 10L);
    }

    @Test
    void readingUnpublishedEventDoesNotRecordView() {
        event.setState(EventState.PENDING);
        assertThatThrownBy(() -> service.getPublicEvent(10L, 1L))
                .isInstanceOf(ru.practicum.ewm.exception.NotFoundException.class);
        verifyNoInteractions(stats);
    }

    @Test
    void confirmedParticipantCanLike() {
        when(requests.hasConfirmedParticipation(10, 1)).thenReturn(true);
        service.likeEvent(1, 10);
        verify(stats).like(1, 10);
    }

    @Test
    void unconfirmedParticipantCannotLike() {
        assertThatThrownBy(() -> service.likeEvent(1, 10)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stats);
    }

    @Test
    void unpublishedEventCannotBeLiked() {
        event.setState(EventState.CANCELED);
        assertThatThrownBy(() -> service.likeEvent(1, 10)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stats);
    }
}
