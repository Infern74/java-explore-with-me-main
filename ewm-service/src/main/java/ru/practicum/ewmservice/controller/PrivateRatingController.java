package ru.practicum.ewmservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewmservice.dto.EventRatingDto;
import ru.practicum.ewmservice.dto.RatingRequest;
import ru.practicum.ewmservice.service.RatingService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/ratings")
public class PrivateRatingController {

    private final RatingService ratingService;

    @PostMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.CREATED)
    public EventRatingDto rateEvent(@PathVariable Long userId,
                                    @PathVariable Long eventId,
                                    @RequestBody RatingRequest ratingRequest) {
        return ratingService.rateEvent(userId, eventId, ratingRequest);
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRating(@PathVariable Long userId,
                             @PathVariable Long eventId) {
        ratingService.removeRating(userId, eventId);
    }

    @PatchMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.OK)
    public EventRatingDto updateRating(@PathVariable Long userId,
                                       @PathVariable Long eventId,
                                       @RequestBody RatingRequest ratingRequest) {
        return ratingService.updateRating(userId, eventId, ratingRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<EventRatingDto> getUserRatings(@PathVariable Long userId) {
        return ratingService.getUserRatings(userId);
    }

    @GetMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.OK)
    public EventRatingDto getUserRatingForEvent(@PathVariable Long userId,
                                                @PathVariable Long eventId) {
        return ratingService.getUserRatingForEvent(userId, eventId);
    }
}