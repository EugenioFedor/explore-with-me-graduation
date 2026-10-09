package ru.practicum.ewm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.ewm.model.EventState;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventForRequestDto {

    private Long id;
    private Long initiatorId;
    private EventState state;
    private Integer participantLimit;
    private Boolean requestModeration;
}