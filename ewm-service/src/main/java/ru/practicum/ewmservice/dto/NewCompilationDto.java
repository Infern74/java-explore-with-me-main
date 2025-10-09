package ru.practicum.ewmservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewCompilationDto {
    @NotBlank(message = "Title cannot be empty")
    @Size(min = 1, max = 50, message = "Title must be between 1 and 50 characters")
    private String title;

    private Boolean pinned = false;

    private List<Long> events;
}