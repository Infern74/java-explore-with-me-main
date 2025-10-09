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
public class CompilationDto {
    private Long id;

    @NotBlank
    @Size(min = 1, max = 50)
    private String title;

    private Boolean pinned = false;

    private List<EventShortDto> events;
}