package ru.practicum.ewm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventForRequestDto {
    private Long id;
    private Long initiatorId;
    private String state;
    private Integer participantLimit;
    private Boolean requestModeration;
}
