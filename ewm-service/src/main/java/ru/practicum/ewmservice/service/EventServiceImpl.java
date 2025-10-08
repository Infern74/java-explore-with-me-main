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