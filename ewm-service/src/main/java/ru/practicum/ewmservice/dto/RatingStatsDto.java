package ru.practicum.ewmservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RatingStatsDto {
    private Long eventId;
    private String eventTitle;
    private Long authorId;
    private String authorName;
    private Long likes;
    private Long dislikes;
    private Long rating; // likes - dislikes
}