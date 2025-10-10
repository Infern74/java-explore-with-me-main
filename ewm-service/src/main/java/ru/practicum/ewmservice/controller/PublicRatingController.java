package ru.practicum.ewmservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewmservice.dto.RatingStatsDto;
import ru.practicum.ewmservice.service.RatingService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ratings")
public class PublicRatingController {

    private final RatingService ratingService;

    @GetMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.OK)
    public RatingStatsDto getEventRatingStats(@PathVariable Long eventId) {
        return ratingService.getEventRatingStats(eventId);
    }

    @GetMapping("/events/top")
    @ResponseStatus(HttpStatus.OK)
    public List<RatingStatsDto> getTopRatedEvents(
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size) {
        return ratingService.getTopRatedEvents(from, size);
    }

    @GetMapping("/users/top")
    @ResponseStatus(HttpStatus.OK)
    public List<RatingStatsDto> getTopRatedUsers(
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size) {
        return ratingService.getTopRatedUsers(from, size);
    }
}