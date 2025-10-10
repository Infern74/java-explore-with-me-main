package ru.practicum.ewmservice.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.ewmservice.dto.EventRatingDto;
import ru.practicum.ewmservice.model.EventRating;

import java.time.format.DateTimeFormatter;

@UtilityClass
public class EventRatingMapper {

    public static EventRatingDto toEventRatingDto(EventRating rating, DateTimeFormatter formatter) {
        if (rating == null) {
            return null;
        }

        EventRatingDto dto = new EventRatingDto();
        dto.setId(rating.getId());
        dto.setUserId(rating.getUser().getId());
        dto.setEventId(rating.getEvent().getId());
        dto.setIsLike(rating.getIsLike());
        dto.setCreated(rating.getCreated().format(formatter));

        return dto;
    }
}