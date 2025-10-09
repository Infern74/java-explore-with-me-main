package ru.practicum.ewmservice.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ErrorMessage {
    private final int statusCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp;

    private final String message;
    private final String description;

    public ErrorMessage(int statusCode, String message, String description) {
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now();
        this.message = message;
        this.description = description;
    }
}