package com.example.sseproject.dto;

public record SseEvent(Long id, String message, String timestamp, String type) {

}
