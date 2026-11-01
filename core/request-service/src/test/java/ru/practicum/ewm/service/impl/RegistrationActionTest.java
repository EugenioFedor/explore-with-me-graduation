package ru.practicum.ewm.service.impl;

import org.junit.jupiter.api.Test;
import ru.practicum.ewm.client.EventClient;
import ru.practicum.ewm.client.UserClient;
import ru.practicum.ewm.dto.EventForRequestDto;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.stats.client.CollectorClient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RegistrationActionTest {
    private final RequestRepository repository = mock(RequestRepository.class);
    private final EventClient events = mock(EventClient.class);
    private final CollectorClient collector = mock(CollectorClient.class);
    private final RequestServiceImpl service = new RequestServiceImpl(repository, events, mock(UserClient.class),
            mock(RequestMapper.class), collector);

    @Test
    void acceptedRequestRecordsRegisterAction() {
        var event = new EventForRequestDto();
        event.setInitiatorId(2L);
        event.setState("PUBLISHED");
        event.setParticipantLimit(0);
        when(events.getEvent(10L)).thenReturn(event);
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service.addRequest(1L, 10L);
        verify(collector).collect(1L, 10L, ActionTypeProto.ACTION_REGISTER);
    }

    @Test
    void rejectedRequestDoesNotRecordAction() {
        var event = new EventForRequestDto();
        event.setInitiatorId(1L);
        when(events.getEvent(10L)).thenReturn(event);
        assertThatThrownBy(() -> service.addRequest(1L, 10L)).isInstanceOf(ru.practicum.ewm.exception.ConflictException.class);
        verifyNoInteractions(collector);
    }
}
