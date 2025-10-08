package ru.practicum.ewmservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorMessage handleNotFoundException(NotFoundException ex, HttpServletRequest request) {
        log.error("NotFoundException: {}", ex.getMessage());
        return new ErrorMessage(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleValidationException(ValidationException ex, HttpServletRequest request) {
        log.error("ValidationException: {}", ex.getMessage());
        return new ErrorMessage(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorMessage handleConflictException(ConflictException ex, HttpServletRequest request) {
        log.error("ConflictException: {}", ex.getMessage());
        return new ErrorMessage(HttpStatus.CONFLICT.value(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        String errorMessage = "Validation failed: " + String.join("; ", errors);
        log.error("MethodArgumentNotValidException: {}", errorMessage);

        return new ErrorMessage(HttpStatus.BAD_REQUEST.value(), errorMessage, request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        log.error("ConstraintViolationException: {}", ex.getMessage());
        return new ErrorMessage(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleMissingServletRequestParameterException(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.error("MissingServletRequestParameterException: {}", ex.getMessage());
        return new ErrorMessage(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.error("HttpMessageNotReadableException: {}", ex.getMessage());
        String message = "Required request body is missing";
        if (ex.getMessage() != null && ex.getMessage().contains("Required request body is missing")) {
            message = "Required request body is missing";
        }
        return new ErrorMessage(HttpStatus.BAD_REQUEST.value(), message, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorMessage handleGlobalException(Exception ex, HttpServletRequest request) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        return new ErrorMessage(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.error("IllegalArgumentException: {}", ex.getMessage());
        return new ErrorMessage(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI());
    }
}