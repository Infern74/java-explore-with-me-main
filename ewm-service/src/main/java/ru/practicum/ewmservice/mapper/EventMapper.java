package ru.practicum.ewmservice.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.ewmservice.dto.EventFullDto;
import ru.practicum.ewmservice.dto.EventShortDto;
import ru.practicum.ewmservice.model.Event;

@UtilityClass
public class EventMapper {
    public EventFullDto toEventFullDto(Event event) {
        return new EventFullDto(
                event.getId(),
                event.getTitle(),
                event.getAnnotation(),
                event.getDescription(),
                CategoryMapper.toCategoryDto(event.getCategory()),
                UserMapper.toUserDto(event.getInitiator()),
                event.getEventDate(),
                event.getCreatedOn(),
                event.getPublishedOn(),
                event.getState(),
                event.getLocation(),
                event.getPaid(),
                event.getParticipantLimit(),
                event.getRequestModeration(),
                event.getViews(),
                event.getConfirmedRequests()
        );
    }

    public EventShortDto toEventShortDto(Event event) {
        return new EventShortDto(
                event.getId(),
                event.getTitle(),
                event.getAnnotation(),
                CategoryMapper.toCategoryDto(event.getCategory()),
                UserMapper.toUserDto(event.getInitiator()),
                event.getEventDate(),
                event.getPaid(),
                event.getViews(),
                event.getConfirmedRequests()
        );
    }
}