package ru.practicum.ewmservice.service;

import ru.practicum.ewmservice.dto.EventFullDto;
import ru.practicum.ewmservice.dto.EventShortDto;
import ru.practicum.ewmservice.dto.UpdateEventAdminRequest;
import ru.practicum.ewmservice.model.EventState;

import java.time.LocalDateTime;
import java.util.List;

public interface EventService {
    List<EventShortDto> getEvents(String text, List<Long> categories, Boolean paid,
                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                  Boolean onlyAvailable, String sort, Integer from, Integer size);

    EventFullDto getEventById(Long eventId);

    List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                        LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                        Integer from, Integer size);

    EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateEventAdminRequest);
}