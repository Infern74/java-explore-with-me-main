package ru.practicum.ewmservice.service;

import ru.practicum.ewmservice.dto.EventRatingDto;
import ru.practicum.ewmservice.dto.RatingRequest;
import ru.practicum.ewmservice.dto.RatingStatsDto;

import java.util.List;

public interface RatingService {
    EventRatingDto rateEvent(Long userId, Long eventId, RatingRequest ratingRequest);

    void removeRating(Long userId, Long eventId);

    EventRatingDto updateRating(Long userId, Long eventId, RatingRequest ratingRequest);

    List<EventRatingDto> getUserRatings(Long userId);

    RatingStatsDto getEventRatingStats(Long eventId);

    List<RatingStatsDto> getTopRatedEvents(Integer from, Integer size);

    List<RatingStatsDto> getTopRatedUsers(Integer from, Integer size);

    EventRatingDto getUserRatingForEvent(Long userId, Long eventId);
}