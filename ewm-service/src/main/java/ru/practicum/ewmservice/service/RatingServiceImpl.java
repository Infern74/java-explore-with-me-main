package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewmservice.dto.EventRatingDto;
import ru.practicum.ewmservice.dto.RatingRequest;
import ru.practicum.ewmservice.dto.RatingStatsDto;
import ru.practicum.ewmservice.exception.ConflictException;
import ru.practicum.ewmservice.exception.NotFoundException;
import ru.practicum.ewmservice.exception.ValidationException;
import ru.practicum.ewmservice.mapper.EventRatingMapper;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.model.EventRating;
import ru.practicum.ewmservice.model.EventState;
import ru.practicum.ewmservice.model.User;
import ru.practicum.ewmservice.repository.EventRatingRepository;
import ru.practicum.ewmservice.repository.EventRepository;
import ru.practicum.ewmservice.repository.UserRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingServiceImpl implements RatingService {

    private final EventRatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public EventRatingDto rateEvent(Long userId, Long eventId, RatingRequest ratingRequest) {
        User user = getUserByIdOrThrow(userId);
        Event event = getEventByIdOrThrow(eventId);

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Cannot rate unpublished event");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("User cannot rate their own event");
        }

        // Проверяем, не оценивал ли уже пользователь это событие
        ratingRepository.findByUserIdAndEventId(userId, eventId)
                .ifPresent(rating -> {
                    throw new ConflictException("User has already rated this event");
                });

        EventRating rating = new EventRating();
        rating.setUser(user);
        rating.setEvent(event);
        rating.setIsLike(ratingRequest.getIsLike());

        EventRating savedRating = ratingRepository.save(rating);
        log.info("User {} {} event {}", userId, ratingRequest.getIsLike() ? "liked" : "disliked", eventId);

        return EventRatingMapper.toEventRatingDto(savedRating, formatter);
    }

    @Override
    @Transactional
    public void removeRating(Long userId, Long eventId) {
        EventRating rating = ratingRepository.findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Rating not found for user " + userId + " and event " + eventId));

        ratingRepository.delete(rating);
        log.info("User {} removed rating for event {}", userId, eventId);
    }

    @Override
    @Transactional
    public EventRatingDto updateRating(Long userId, Long eventId, RatingRequest ratingRequest) {
        EventRating rating = ratingRepository.findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Rating not found for user " + userId + " and event " + eventId));

        rating.setIsLike(ratingRequest.getIsLike());
        EventRating updatedRating = ratingRepository.save(rating);
        log.info("User {} updated rating for event {} to {}", userId, eventId, ratingRequest.getIsLike() ? "like" : "dislike");

        return EventRatingMapper.toEventRatingDto(updatedRating, formatter);
    }

    @Override
    public List<EventRatingDto> getUserRatings(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }

        return ratingRepository.findByUserId(userId).stream()
                .map(rating -> EventRatingMapper.toEventRatingDto(rating, formatter))
                .collect(Collectors.toList());
    }

    @Override
    public RatingStatsDto getEventRatingStats(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Event with id=" + eventId + " not found");
        }

        Long likes = ratingRepository.countLikesByEventId(eventId);
        Long dislikes = ratingRepository.countDislikesByEventId(eventId);
        Event event = eventRepository.findById(eventId).orElseThrow();

        return new RatingStatsDto(
                eventId,
                event.getTitle(),
                event.getInitiator().getId(),
                event.getInitiator().getName(),
                likes,
                dislikes,
                likes - dislikes
        );
    }

    @Override
    public List<RatingStatsDto> getTopRatedEvents(Integer from, Integer size) {
        validatePaginationParams(from, size);
        Pageable pageable = PageRequest.of(from / size, size);

        List<RatingStatsDto> allStats = ratingRepository.getEventsRatingStats();
        return allStats.stream()
                .skip(from)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public List<RatingStatsDto> getTopRatedUsers(Integer from, Integer size) {
        validatePaginationParams(from, size);

        // Получаем всех пользователей, у которых есть события
        List<User> usersWithEvents = userRepository.findAll().stream()
                .filter(user -> !eventRepository.findByInitiatorId(user.getId(), PageRequest.of(0, 1)).isEmpty())
                .collect(Collectors.toList());

        List<Long> userIds = usersWithEvents.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        // Получаем рейтинги пользователей
        List<Object[]> userRatings = ratingRepository.getRatingsByUserIds(userIds);
        Map<Long, Long> userRatingMap = userRatings.stream()
                .collect(Collectors.toMap(
                        arr -> (Long) arr[0],
                        arr -> (Long) arr[1]
                ));

        // Создаем DTO и сортируем по рейтингу
        return usersWithEvents.stream()
                .map(user -> {
                    Long rating = userRatingMap.getOrDefault(user.getId(), 0L);
                    return new RatingStatsDto(
                            null, null, user.getId(), user.getName(),
                            null, null, rating
                    );
                })
                .sorted((a, b) -> Long.compare(b.getRating(), a.getRating()))
                .skip(from)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public EventRatingDto getUserRatingForEvent(Long userId, Long eventId) {
        EventRating rating = ratingRepository.findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Rating not found for user " + userId + " and event " + eventId));

        return EventRatingMapper.toEventRatingDto(rating, formatter);
    }

    private User getUserByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));
    }

    private Event getEventByIdOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));
    }

    private void validatePaginationParams(Integer from, Integer size) {
        if (from < 0) {
            throw new ValidationException("From must be non-negative");
        }
        if (size <= 0) {
            throw new ValidationException("Size must be positive");
        }
    }
}