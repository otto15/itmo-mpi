package com.drakkar.erp.api;

import com.drakkar.erp.domain.DomainException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiModels.ErrorResponse> domain(DomainException exception) {
        return ResponseEntity.status(exception.status()).body(
                new ApiModels.ErrorResponse(exception.code(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiModels.ErrorResponse> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Некорректные данные");
        return ResponseEntity.badRequest().body(
                new ApiModels.ErrorResponse("VALIDATION_ERROR", message, Instant.now()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiModels.ErrorResponse> database(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiModels.ErrorResponse(
                        "DATABASE_INVARIANT",
                        "Операция отклонена ограничением целостности базы данных",
                        Instant.now()));
    }
}
