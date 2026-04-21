package com.example.sseproject.dto;

public record SendResult(boolean success, String message, SseEvent event) {
    public SendResult(boolean success, String message) {
        this(success, message, null);
    }
}