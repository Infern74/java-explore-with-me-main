package ru.practicum.ewmservice.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.ewmservice.dto.EventFullDto;
import ru.practicum.ewmservice.dto.EventShortDto;
import ru.practicum.ewmservice.model.Event;

@UtilityClass
public class EventMapper {

    public EventFullDto toEventFullDto(Event event, Long views, Long confirmedRequests) {
        EventFullDto dto = new EventFullDto();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setAnnotation(event.getAnnotation());
        dto.setDescription(event.getDescription());
        dto.setCategory(CategoryMapper.toCategoryDto(event.getCategory()));
        dto.setInitiator(UserMapper.toUserDto(event.getInitiator()));
        dto.setEventDate(event.getEventDate());
        dto.setCreatedOn(event.getCreatedOn());
        dto.setPublishedOn(event.getPublishedOn());
        dto.setState(event.getState());
        dto.setLocation(event.getLocation());
        dto.setPaid(event.getPaid());
        dto.setParticipantLimit(event.getParticipantLimit());
        dto.setRequestModeration(event.getRequestModeration());
        dto.setViews(views);
        dto.setConfirmedRequests(confirmedRequests);
        return dto;
    }

    public EventShortDto toEventShortDto(Event event, Long views, Long confirmedRequests) {
        EventShortDto dto = new EventShortDto();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setAnnotation(event.getAnnotation());
        dto.setCategory(CategoryMapper.toCategoryDto(event.getCategory()));
        dto.setInitiator(UserMapper.toUserDto(event.getInitiator()));
        dto.setEventDate(event.getEventDate());
        dto.setPaid(event.getPaid());
        dto.setViews(views);
        dto.setConfirmedRequests(confirmedRequests);
        return dto;
    }
}