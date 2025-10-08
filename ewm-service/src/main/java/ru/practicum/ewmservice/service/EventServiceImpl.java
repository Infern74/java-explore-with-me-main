package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.ViewStats;
import ru.practicum.ewmservice.dto.EventFullDto;
import ru.practicum.ewmservice.dto.EventShortDto;
import ru.practicum.ewmservice.dto.UpdateEventAdminRequest;
import ru.practicum.ewmservice.exception.ConflictException;
import ru.practicum.ewmservice.exception.NotFoundException;
import ru.practicum.ewmservice.exception.ValidationException;
import ru.practicum.ewmservice.mapper.EventMapper;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.model.EventState;
import ru.practicum.ewmservice.repository.EventRepository;
import ru.practicum.ewmservice.repository.EventSpecifications;
import ru.practicum.ewmservice.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final StatsIntegrationService statsIntegrationService;
    private final ParticipationRequestRepository requestRepository;

    @Override
    public List<EventShortDto> getEvents(String text, List<Long> categories, Boolean paid,
                                         LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                         Boolean onlyAvailable, String sort, Integer from, Integer size) {
        log.info("Getting events with parameters: text={}, categories={}, paid={}, rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

        validatePaginationParams(from, size);

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Start date must be before end date");
        }

        Specification<Event> specification = Specification.where(EventSpecifications.isPublished())
                .and(EventSpecifications.hasTextInAnnotationOrDescription(text))
                .and(EventSpecifications.hasCategories(categories))
                .and(EventSpecifications.isPaid(paid))
                .and(EventSpecifications.isInDateRange(rangeStart, rangeEnd));

        Pageable pageable = createPageable(from, size, sort);

        List<Event> events = eventRepository.findAll(specification, pageable).getContent();

        if (onlyAvailable != null && onlyAvailable) {
            events = events.stream()
                    .filter(event -> isEventAvailable(event))
                    .collect(Collectors.toList());
        }

        Map<Long, Long> viewsMap = getEventsViews(events);
        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(events);

        return events.stream()
                .map(event -> {
                    Long eventViews = viewsMap.getOrDefault(event.getId(), 0L);
                    Long confirmedRequests = confirmedRequestsMap.getOrDefault(event.getId(), 0L);
                    return EventMapper.toEventShortDto(event, eventViews, confirmedRequests);
                })
                .collect(Collectors.toList());
    }

    @Override
    public EventFullDto getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " is not published");
        }

        Long views = getEventViews(eventId);
        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(eventId);
        return EventMapper.toEventFullDto(event, views, confirmedRequests);
    }

    @Override
    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Integer from, Integer size) {
        log.info("Getting events by admin: users={}, states={}, categories={}, rangeStart={}, rangeEnd={}, from={}, size={}",
                users, states, categories, rangeStart, rangeEnd, from, size);

        validatePaginationParams(from, size);

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Start date must be before end date");
        }

        Specification<Event> specification = Specification.where(EventSpecifications.hasUsers(users))
                .and(EventSpecifications.hasStates(states))
                .and(EventSpecifications.hasCategories(categories))
                .and(EventSpecifications.isInDateRange(rangeStart, rangeEnd));

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<Event> events = eventRepository.findAll(specification, pageable).getContent();

        Map<Long, Long> viewsMap = getEventsViews(events);
        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(events);

        return events.stream()
                .map(event -> {
                    Long eventViews = viewsMap.getOrDefault(event.getId(), 0L);
                    Long confirmedRequests = confirmedRequestsMap.getOrDefault(event.getId(), 0L);
                    return EventMapper.toEventFullDto(event, eventViews, confirmedRequests);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateEventAdminRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        // Проверка на публикацию/отклонение
        if (updateEventAdminRequest.getStateAction() != null) {
            if ("PUBLISH_EVENT".equals(updateEventAdminRequest.getStateAction())) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                    throw new ValidationException("Cannot publish event because it starts in less than 1 hour");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if ("REJECT_EVENT".equals(updateEventAdminRequest.getStateAction())) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject the event because it's already published");
                }
                event.setState(EventState.CANCELED);
            } else {
                throw new ValidationException("Invalid state action: " + updateEventAdminRequest.getStateAction());
            }
        }

        // Обновление полей
        updateEventFields(event, updateEventAdminRequest);

        Event updatedEvent = eventRepository.save(event);

        Long views = getEventViews(eventId);
        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(eventId);
        return EventMapper.toEventFullDto(updatedEvent, views, confirmedRequests);
    }

    private Pageable createPageable(Integer from, Integer size, String sort) {
        Sort sorting;
        if ("VIEWS".equals(sort)) {
            sorting = Sort.by("id").ascending(); // Сортировка по views будет на уровне приложения
        } else if ("EVENT_DATE".equals(sort)) {
            sorting = Sort.by("eventDate").descending();
        } else {
            sorting = Sort.by("id").ascending();
        }
        return PageRequest.of(from > 0 ? from / size : 0, size, sorting);
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest updateRequest) {
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            String annotation = updateRequest.getAnnotation().trim();
            if (annotation.length() < 20 || annotation.length() > 2000) {
                throw new ValidationException("Annotation must be between 20 and 2000 characters");
            }
            event.setAnnotation(annotation);
        }

        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            String description = updateRequest.getDescription().trim();
            if (description.length() < 20 || description.length() > 7000) {
                throw new ValidationException("Description must be between 20 and 7000 characters");
            }
            event.setDescription(description);
        }

        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            String title = updateRequest.getTitle().trim();
            if (title.length() < 3 || title.length() > 120) {
                throw new ValidationException("Title must be between 3 and 120 characters");
            }
            event.setTitle(title);
        }

        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now())) {
                throw new ValidationException("Event date must be in the future");
            }
            event.setEventDate(updateRequest.getEventDate());
        }

        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }

        if (updateRequest.getParticipantLimit() != null) {
            if (updateRequest.getParticipantLimit() < 0) {
                throw new ValidationException("Participant limit cannot be negative");
            }
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }

        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
    }

    private Map<Long, Long> getEventsViews(List<Event> events) {
        if (events.isEmpty()) {
            return new HashMap<>();
        }

        try {
            List<String> uris = events.stream()
                    .map(event -> "/events/" + event.getId())
                    .collect(Collectors.toList());

            LocalDateTime start = events.stream()
                    .map(Event::getCreatedOn)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now().minusYears(1));

            LocalDateTime end = LocalDateTime.now().plusHours(1);

            List<ViewStats> stats = statsIntegrationService.getStats(start, end, uris, true);

            Map<Long, Long> views = new HashMap<>();
            for (ViewStats stat : stats) {
                Long eventId = extractEventIdFromUri(stat.getUri());
                if (eventId != null) {
                    views.put(eventId, stat.getHits());
                }
            }

            return views;
        } catch (Exception e) {
            log.warn("Failed to get views from stats service: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Event> events) {
        return events.stream()
                .collect(Collectors.toMap(
                        Event::getId,
                        event -> requestRepository.getConfirmedRequestsCount(event.getId())
                ));
    }

    private Long getEventViews(Long eventId) {
        try {
            String uri = "/events/" + eventId;
            List<String> uris = List.of(uri);

            LocalDateTime start = LocalDateTime.now().minusYears(1);
            LocalDateTime end = LocalDateTime.now().plusHours(1);

            List<ViewStats> stats = statsIntegrationService.getStats(start, end, uris, true);

            if (!stats.isEmpty()) {
                return stats.get(0).getHits();
            }
        } catch (Exception e) {
            log.warn("Failed to get views for event {}: {}", eventId, e.getMessage());
        }

        return 0L;
    }

    private Long extractEventIdFromUri(String uri) {
        try {
            String[] parts = uri.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    private boolean isEventAvailable(Event event) {
        if (event.getParticipantLimit() == 0) {
            return true;
        }
        Long confirmedRequests = requestRepository.getConfirmedRequestsCount(event.getId());
        return confirmedRequests < event.getParticipantLimit();
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