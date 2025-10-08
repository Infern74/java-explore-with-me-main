package ru.practicum.ewmservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.ewmservice.model.Location;

import javax.validation.constraints.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewEventDto {

    @NotBlank(message = "Annotation cannot be empty")
    @Size(min = 20, max = 2000, message = "Annotation must be between 20 and 2000 characters")
    private String annotation;

    @NotNull(message = "Category cannot be null")
    private Long category;

    @NotBlank(message = "Description cannot be empty")
    @Size(min = 20, max = 7000, message = "Description must be between 20 and 7000 characters")
    private String description;

    @NotNull(message = "EventDate cannot be null")
    @Future(message = "EventDate must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;

    @NotNull(message = "Location cannot be null")
    private Location location;

    private Boolean paid = false;

    @PositiveOrZero(message = "Participant limit cannot be negative")
    private Integer participantLimit = 0;

    private Boolean requestModeration = true;

    @NotBlank(message = "Title cannot be empty")
    @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters")
    private String title;
}