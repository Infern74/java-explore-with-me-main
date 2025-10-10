package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewmservice.dto.EventFullDto;
import ru.practicum.ewmservice.dto.EventShortDto;
import ru.practicum.ewmservice.dto.NewEventDto;
import ru.practicum.ewmservice.dto.UpdateEventUserRequest;
import ru.practicum.ewmservice.exception.ConflictException;
import ru.practicum.ewmservice.exception.NotFoundException;
import ru.practicum.ewmservice.exception.ValidationException;
import ru.practicum.ewmservice.mapper.EventMapper;
import ru.practicum.ewmservice.model.Category;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.model.EventState;
import ru.practicum.ewmservice.model.User;
import ru.practicum.ewmservice.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrivateEventServiceImpl implements PrivateEventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatsIntegrationService statsIntegrationService;
    private final EventRatingRepository ratingRepository;

    @Override
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        if (from < 0) {
            throw new ValidationException("From must be non-negative");
        }
        if (size <= 0) {
            throw new ValidationException("Size must be positive");
        }

        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable);

        return events.stream()
                .map(event -> {
                    Long views = statsIntegrationService.getEventViews(event.getId());
                    Long confirmedRequests = requestRepository.getConfirmedRequestsCount(event.getId());
                    Long likes = ratingRepository.countLikesByEventId(event.getId());
                    Long dislikes = ratingRepository.countDislikesByEventId(event.getId());
                    EventShortDto dto = EventMapper.toEventShortDto(event, views, confirmedRequests);
                    dto.setLikes(likes);
                    dto.setDislikes(dislikes);
                    dto.setRating(likes - dislikes);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        User user = getUserByIdOrThrow(userId);
        Category category = getCategoryByIdOrThrow(newEventDto.getCategory());

        validateEventFields(newEventDto);

        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }

        Event event = EventMapper.toEvent(newEventDto, user, category);
        Event savedEvent = eventRepository.save(event);

        EventFullDto dto = EventMapper.toEventFullDto(savedEvent, 0L, 0L);
        dto.setLikes(0L);
        dto.setDislikes(0L);
        dto.setRating(0L);

        return dto;
    }

    @Override
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = getEventByIdAndInitiatorIdOrThrow(eventId, userId);

        Long views = statsIntegrationService.getEventViews(eventId);
        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(eventId);
        Long likes = ratingRepository.countLikesByEventId(eventId);
        Long dislikes = ratingRepository.countDislikesByEventId(eventId);

        EventFullDto dto = EventMapper.toEventFullDto(event, views, confirmedRequests);
        dto.setLikes(likes);
        dto.setDislikes(dislikes);
        dto.setRating(likes - dislikes);

        return dto;
    }

    @Override
    @Transactional
    public EventFullDto updateEvent(Long userId, Long eventId, UpdateEventUserRequest updateEventUserRequest) {
        Event event = getEventByIdAndInitiatorIdOrThrow(eventId, userId);

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        validateUpdateEventFields(updateEventUserRequest);

        updateEventFields(event, updateEventUserRequest);

        if (updateEventUserRequest.getStateAction() != null) {
            if ("SEND_TO_REVIEW".equals(updateEventUserRequest.getStateAction())) {
                event.setState(EventState.PENDING);
            } else if ("CANCEL_REVIEW".equals(updateEventUserRequest.getStateAction())) {
                event.setState(EventState.CANCELED);
            } else {
                throw new ValidationException("Invalid state action: " + updateEventUserRequest.getStateAction());
            }
        }

        Event updatedEvent = eventRepository.save(event);

        Long views = statsIntegrationService.getEventViews(eventId);
        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(eventId);
        Long likes = ratingRepository.countLikesByEventId(eventId);
        Long dislikes = ratingRepository.countDislikesByEventId(eventId);

        EventFullDto dto = EventMapper.toEventFullDto(updatedEvent, views, confirmedRequests);
        dto.setLikes(likes);
        dto.setDislikes(dislikes);
        dto.setRating(likes - dislikes);

        return dto;
    }

    private User getUserByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));
    }

    private Category getCategoryByIdOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + categoryId + " not found"));
    }

    private Event getEventByIdAndInitiatorIdOrThrow(Long eventId, Long userId) {
        return eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found for user=" + userId));
    }

    private void validateEventFields(NewEventDto newEventDto) {
        if (newEventDto.getAnnotation() == null || newEventDto.getAnnotation().trim().isEmpty()) {
            throw new ValidationException("Annotation cannot be empty");
        }
        String annotation = newEventDto.getAnnotation().trim();
        if (annotation.length() < 20 || annotation.length() > 2000) {
            throw new ValidationException("Annotation must be between 20 and 2000 characters");
        }

        if (newEventDto.getDescription() == null || newEventDto.getDescription().trim().isEmpty()) {
            throw new ValidationException("Description cannot be empty");
        }
        String description = newEventDto.getDescription().trim();
        if (description.length() < 20 || description.length() > 7000) {
            throw new ValidationException("Description must be between 20 and 7000 characters");
        }

        if (newEventDto.getTitle() == null || newEventDto.getTitle().trim().isEmpty()) {
            throw new ValidationException("Title cannot be empty");
        }
        String title = newEventDto.getTitle().trim();
        if (title.length() < 3 || title.length() > 120) {
            throw new ValidationException("Title must be between 3 and 120 characters");
        }

        if (newEventDto.getParticipantLimit() != null && newEventDto.getParticipantLimit() < 0) {
            throw new ValidationException("Participant limit cannot be negative");
        }
    }

    private void validateUpdateEventFields(UpdateEventUserRequest updateRequest) {
        if (updateRequest.getEventDate() != null &&
                updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }

        if (updateRequest.getAnnotation() != null) {
            String annotation = updateRequest.getAnnotation().trim();
            if (annotation.isEmpty()) {
                throw new ValidationException("Annotation cannot be empty");
            }
            if (annotation.length() < 20 || annotation.length() > 2000) {
                throw new ValidationException("Annotation must be between 20 and 2000 characters");
            }
        }

        if (updateRequest.getDescription() != null) {
            String description = updateRequest.getDescription().trim();
            if (description.isEmpty()) {
                throw new ValidationException("Description cannot be empty");
            }
            if (description.length() < 20 || description.length() > 7000) {
                throw new ValidationException("Description must be between 20 and 7000 characters");
            }
        }

        if (updateRequest.getTitle() != null) {
            String title = updateRequest.getTitle().trim();
            if (title.isEmpty()) {
                throw new ValidationException("Title cannot be empty");
            }
            if (title.length() < 3 || title.length() > 120) {
                throw new ValidationException("Title must be between 3 and 120 characters");
            }
        }

        if (updateRequest.getParticipantLimit() != null && updateRequest.getParticipantLimit() < 0) {
            throw new ValidationException("Participant limit cannot be negative");
        }
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateRequest) {
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle().trim());
        }
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateRequest.getAnnotation().trim());
        }
        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            event.setDescription(updateRequest.getDescription().trim());
        }
        if (updateRequest.getCategory() != null) {
            Category category = getCategoryByIdOrThrow(updateRequest.getCategory());
            event.setCategory(category);
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(updateRequest.getLocation());
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
    }
}