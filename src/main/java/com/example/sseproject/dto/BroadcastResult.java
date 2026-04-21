package com.example.sseproject.dto;

public record BroadcastResult(int successCount, int failCount, SseEvent event) {
}
