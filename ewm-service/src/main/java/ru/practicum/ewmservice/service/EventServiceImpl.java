package ru.practicum.ewmservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final StatsIntegrationService statsIntegrationService;

    @Override
    public List<EventShortDto> getEvents(String text, List<Long> categories, Boolean paid,
                                         LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                         Boolean onlyAvailable, String sort, Integer from, Integer size) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Start date must be before end date");
        }

        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        LocalDateTime end = rangeEnd != null ? rangeEnd : LocalDateTime.now().plusYears(100);

        Pageable pageable;
        if ("VIEWS".equals(sort)) {
            pageable = PageRequest.of(from / size, size, Sort.by("views").descending());
        } else {
            pageable = PageRequest.of(from / size, size, Sort.by("eventDate").descending());
        }

        List<Event> events = eventRepository.findEventsByPublic(
                text, categories, paid, start, end, pageable);

        if (onlyAvailable != null && onlyAvailable) {
            events = events.stream()
                    .filter(event -> event.getParticipantLimit() == 0 ||
                            event.getConfirmedRequests() < event.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        // Получаем статистику просмотров для событий
        Map<Long, Long> views = getEventsViews(events);

        // Обновляем события с актуальным количеством просмотров
        List<Event> updatedEvents = events.stream()
                .map(event -> {
                    event.setViews(views.getOrDefault(event.getId(), 0L));
                    return event;
                })
                .toList();

        return updatedEvents.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    @Override
    public EventFullDto getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " is not published");
        }

        // Получаем актуальное количество просмотров
        Long views = getEventViews(eventId);
        event.setViews(views);
        Event updatedEvent = eventRepository.save(event);

        return EventMapper.toEventFullDto(updatedEvent);
    }

    @Override
    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Integer from, Integer size) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Start date must be before end date");
        }

        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now().minusYears(100);
        LocalDateTime end = rangeEnd != null ? rangeEnd : LocalDateTime.now().plusYears(100);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<Event> events = eventRepository.findEventsByAdmin(
                users, states, categories, start, end, pageable);

        // Получаем статистику просмотров для событий
        Map<Long, Long> views = getEventsViews(events);

        // Обновляем события с актуальным количеством просмотров
        List<Event> updatedEvents = events.stream()
                .map(event -> {
                    event.setViews(views.getOrDefault(event.getId(), 0L));
                    return event;
                })
                .toList();

        return updatedEvents.stream()
                .map(EventMapper::toEventFullDto)
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
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if ("REJECT_EVENT".equals(updateEventAdminRequest.getStateAction())) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject the event because it's already published");
                }
                event.setState(EventState.CANCELED);
            }
        }

        // Обновление полей
        updateEventFields(event, updateEventAdminRequest);

        // Валидация даты при публикации
        if (event.getState() == EventState.PUBLISHED && event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Cannot publish event because it starts in less than 1 hour");
        }

        Event updatedEvent = eventRepository.save(event);

        // Получаем актуальное количество просмотров
        Long views = getEventViews(eventId);
        updatedEvent.setViews(views);

        return EventMapper.toEventFullDto(updatedEvent);
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest updateRequest) {
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            if (updateRequest.getAnnotation().length() < 20 || updateRequest.getAnnotation().length() > 2000) {
                throw new ValidationException("Annotation must be between 20 and 2000 characters");
            }
            event.setAnnotation(updateRequest.getAnnotation());
        }

        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            if (updateRequest.getDescription().length() < 20 || updateRequest.getDescription().length() > 7000) {
                throw new ValidationException("Description must be between 20 and 7000 characters");
            }
            event.setDescription(updateRequest.getDescription());
        }

        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            if (updateRequest.getTitle().length() < 3 || updateRequest.getTitle().length() > 120) {
                throw new ValidationException("Title must be between 3 and 120 characters");
            }
            event.setTitle(updateRequest.getTitle());
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

        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());

        LocalDateTime start = events.stream()
                .map(Event::getCreatedOn)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(1));

        LocalDateTime end = LocalDateTime.now();

        List<ViewStats> stats = statsIntegrationService.getStats(start, end, uris, true);

        Map<Long, Long> views = new HashMap<>();
        for (ViewStats stat : stats) {
            Long eventId = extractEventIdFromUri(stat.getUri());
            if (eventId != null) {
                views.put(eventId, stat.getHits());
            }
        }

        return views;
    }

    private Long getEventViews(Long eventId) {
        String uri = "/events/" + eventId;
        List<String> uris = List.of(uri);

        LocalDateTime start = LocalDateTime.now().minusYears(1);
        LocalDateTime end = LocalDateTime.now();

        List<ViewStats> stats = statsIntegrationService.getStats(start, end, uris, true);

        if (!stats.isEmpty()) {
            return stats.getFirst().getHits();
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
}