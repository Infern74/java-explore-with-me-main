package ru.practicum.ewmservice.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.ewmservice.dto.EventFullDto;
import ru.practicum.ewmservice.dto.EventShortDto;
import ru.practicum.ewmservice.dto.NewEventDto;
import ru.practicum.ewmservice.model.Category;
import ru.practicum.ewmservice.model.Event;
import ru.practicum.ewmservice.model.EventState;
import ru.practicum.ewmservice.model.User;

import java.time.LocalDateTime;

@UtilityClass
public class EventMapper {

    public EventFullDto toEventFullDto(Event event, Long views, Long confirmedRequests) {
        if (event == null) {
            return null;
        }

        EventFullDto dto = new EventFullDto();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setAnnotation(event.getAnnotation());
        dto.setDescription(event.getDescription());

        if (event.getCategory() != null) {
            dto.setCategory(CategoryMapper.toCategoryDto(event.getCategory()));
        }

        if (event.getInitiator() != null) {
            dto.setInitiator(UserMapper.toUserDto(event.getInitiator()));
        }

        dto.setEventDate(event.getEventDate());
        dto.setCreatedOn(event.getCreatedOn());
        dto.setPublishedOn(event.getPublishedOn());
        dto.setState(event.getState());
        dto.setLocation(event.getLocation());
        dto.setPaid(event.getPaid());
        dto.setParticipantLimit(event.getParticipantLimit());
        dto.setRequestModeration(event.getRequestModeration());
        dto.setViews(views != null ? views : 0L);
        dto.setConfirmedRequests(confirmedRequests != null ? confirmedRequests : 0L);

        dto.setLikes(0L);
        dto.setDislikes(0L);
        dto.setRating(0L);

        return dto;
    }

    public EventShortDto toEventShortDto(Event event, Long views, Long confirmedRequests) {
        if (event == null) {
            return null;
        }

        EventShortDto dto = new EventShortDto();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setAnnotation(event.getAnnotation());

        if (event.getCategory() != null) {
            dto.setCategory(CategoryMapper.toCategoryDto(event.getCategory()));
        }

        if (event.getInitiator() != null) {
            dto.setInitiator(UserMapper.toUserDto(event.getInitiator()));
        }

        dto.setEventDate(event.getEventDate());
        dto.setPaid(event.getPaid());
        dto.setViews(views != null ? views : 0L);
        dto.setConfirmedRequests(confirmedRequests != null ? confirmedRequests : 0L);

        dto.setLikes(0L);
        dto.setDislikes(0L);
        dto.setRating(0L);

        return dto;
    }

    public Event toEvent(NewEventDto newEventDto, User initiator, Category category) {
        if (newEventDto == null) {
            return null;
        }

        Event event = new Event();
        event.setTitle(newEventDto.getTitle().trim());
        event.setAnnotation(newEventDto.getAnnotation().trim());
        event.setDescription(newEventDto.getDescription().trim());
        event.setCategory(category);
        event.setInitiator(initiator);
        event.setEventDate(newEventDto.getEventDate());
        event.setCreatedOn(LocalDateTime.now());
        event.setState(EventState.PENDING);
        event.setLocation(newEventDto.getLocation());
        event.setPaid(newEventDto.getPaid() != null ? newEventDto.getPaid() : false);
        event.setParticipantLimit(newEventDto.getParticipantLimit() != null ? newEventDto.getParticipantLimit() : 0);
        event.setRequestModeration(newEventDto.getRequestModeration() != null ? newEventDto.getRequestModeration() : true);

        return event;
    }
}