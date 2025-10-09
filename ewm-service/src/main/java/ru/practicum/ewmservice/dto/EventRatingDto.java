package ru.practicum.ewmservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventRatingDto {
    private Long id;
    private Long userId;
    private Long eventId;
    private Boolean isLike;
    private String created;
}