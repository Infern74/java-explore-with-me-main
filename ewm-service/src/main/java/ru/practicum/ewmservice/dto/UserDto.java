package ru.practicum.ewmservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private Long id;

    @NotBlank(message = "Name cannot be empty")
    @Size(min = 2, max = 250, message = "Name must be between 2 and 250 characters")
    private String name;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email must be valid")
    @Size(min = 6, max = 254, message = "Email must be between 6 and 254 characters")
    private String email;
}