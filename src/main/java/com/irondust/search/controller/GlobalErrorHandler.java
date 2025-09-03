package com.irondust.search.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global error handler to expose useful diagnostics for bad requests during development
 * and to make troubleshooting of request binding issues easier via HTTP responses.
 */
@RestControllerAdvice
public class GlobalErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleBind(WebExchangeBindException ex) {
        List<Map<String, Object>> errors = ex.getAllErrors().stream().map(err -> {
            Map<String, Object> e = new HashMap<>();
            e.put("object", err.getObjectName());
            e.put("code", err.getCode());
            e.put("message", err.getDefaultMessage());
            return e;
        }).collect(Collectors.toList());
        log.warn("Request binding failed: {}", errors);
        Map<String, Object> body = new HashMap<>();
        body.put("error", "bad_request");
        body.put("details", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleInput(ServerWebInputException ex) {
        log.warn("Input error: {}", ex.getReason());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "bad_request");
        body.put("reason", ex.getReason());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.warn("Unhandled error: {}", ex.toString());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "server_error");
        body.put("exception", ex.getClass().getSimpleName());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(500).body(body);
    }
}


