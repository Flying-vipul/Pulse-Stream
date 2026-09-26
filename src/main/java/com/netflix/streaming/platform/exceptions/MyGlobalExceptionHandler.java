package com.netflix.streaming.platform.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class MyGlobalExceptionHandler {

    private Map<String, Object> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        response.put("code", status.name());
        response.put("message", message);
        response.put("path", request.getRequestURI());
        return response;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().iterator().next().getMessage();
        return new ResponseEntity<>(buildResponse(HttpStatus.BAD_REQUEST, message, request), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> myMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        e.getBindingResult().getAllErrors().forEach(err -> {
            String fieldName = ((FieldError)err).getField();
            String message = err.getDefaultMessage();
            sb.append(fieldName).append(": ").append(message).append("; ");
        });
        return new ResponseEntity<>(buildResponse(HttpStatus.BAD_REQUEST, sb.toString(), request), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<Map<String, Object>> myAPIException(APIException e, HttpServletRequest request) {
        return new ResponseEntity<>(buildResponse(HttpStatus.BAD_REQUEST, e.getMessage(), request), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> resourceNotFoundExceptionHandler(ResourceNotFoundException ex, HttpServletRequest request) {
        return new ResponseEntity<>(buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return new ResponseEntity<>(buildResponse(HttpStatus.FORBIDDEN, "Access denied", request), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        return new ResponseEntity<>(buildResponse(HttpStatus.BAD_REQUEST, "Invalid argument provided", request), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        return new ResponseEntity<>(buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
