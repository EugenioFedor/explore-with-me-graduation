package ru.practicum.ewm.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleDataIntegrityViolationException(
            final DataIntegrityViolationException e) {

        log.error("409 {}", e.getMessage());

        return ApiError.builder()
                .errors(List.of(e.getClass().getSimpleName()))
                .message(e.getMessage())
                .reason("Integrity constraint has been violated.")
                .status("CONFLICT")
                .timestamp(LocalDateTime.now())
                .build();
    }
}