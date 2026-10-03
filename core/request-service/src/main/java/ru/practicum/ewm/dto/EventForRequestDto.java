package ru.practicum.ewm.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EventForRequestDto {
    private Long id;
    private Long initiatorId;
    private String state;
    private Integer participantLimit;
    private Boolean requestModeration;
}
