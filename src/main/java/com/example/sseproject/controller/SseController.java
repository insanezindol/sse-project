package com.example.sseproject.controller;

import com.example.sseproject.dto.BroadcastResult;
import com.example.sseproject.dto.HeartbeatEvent;
import com.example.sseproject.dto.MessageRequest;
import com.example.sseproject.dto.SendResult;
import com.example.sseproject.dto.SseEvent;
import com.example.sseproject.dto.TimeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/sse")
@Slf4j
public class SseController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final AtomicLong messageIdGenerator = new AtomicLong(1);
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    public SseController() {
        // 10초마다 모든 클라이언트에게 heartbeat 전송
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            sendHeartbeatToAllClients();
        }, 10, 10, TimeUnit.SECONDS);
    }

    // 모든 클라이언트에게 heartbeat 전송
    private void sendHeartbeatToAllClients() {
        if (emitters.isEmpty()) {
            return;
        }

        HeartbeatEvent heartbeat = new HeartbeatEvent(
                System.currentTimeMillis(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .name("heartbeat")
                        .data(heartbeat));
                log.info("Heartbeat 전송됨 - 클라이언트: " + entry.getKey());
            } catch (IOException e) {
                log.info("Heartbeat 전송 실패 - 클라이언트: " + entry.getKey());
                emitters.remove(entry.getKey());
            }
        }
    }

    // 클라이언트 연결
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(value = "clientId", required = false) String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "client-" + System.currentTimeMillis();
        }

        log.info("새로운 클라이언트 연결: " + clientId);

        // 타임아웃 설정 (30초 -> Long.MAX_VALUE)
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(clientId, emitter);

        // 연결 종료 처리
        String finalClientId = clientId;
        emitter.onCompletion(() -> {
            log.info("연결 종료: " + finalClientId);
            emitters.remove(finalClientId);
        });

        emitter.onTimeout(() -> {
            log.info("연결 타임아웃: " + finalClientId);
            emitters.remove(finalClientId);
        });

        emitter.onError((e) -> {
            log.info("연결 에러: " + finalClientId + ", " + e.getMessage());
            emitters.remove(finalClientId);
        });

        // 초기 연결 메시지 전송
        try {
            SseEvent event = new SseEvent(
                    messageIdGenerator.getAndIncrement(),
                    "연결 성공! 클라이언트 ID: " + clientId,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "CONNECTED"
            );
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.id()))
                    .name("message")
                    .data(event));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // 모든 클라이언트에게 메시지 브로드캐스트
    @PostMapping("/broadcast")
    public BroadcastResult broadcastMessage(@RequestBody MessageRequest request) {
        log.info("브로드캐스트 메시지: " + request.message());

        SseEvent event = new SseEvent(
                messageIdGenerator.getAndIncrement(),
                request.message(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "BROADCAST"
        );

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .id(String.valueOf(event.id()))
                        .name("message")
                        .data(event));
                successCount++;
            } catch (IOException e) {
                failCount++;
                emitters.remove(entry.getKey());
                log.info("메시지 전송 실패 - 클라이언트: " + entry.getKey());
            }
        }

        return new BroadcastResult(successCount, failCount, event);
    }

    // 특정 클라이언트에게 메시지 전송
    @PostMapping("/send/{clientId}")
    public SendResult sendToClient(@PathVariable String clientId, @RequestBody MessageRequest request) {
        SseEmitter emitter = emitters.get(clientId);

        if (emitter == null) {
            return new SendResult(false, "클라이언트를 찾을 수 없습니다: " + clientId);
        }

        SseEvent event = new SseEvent(
                messageIdGenerator.getAndIncrement(),
                request.message(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "DIRECT"
        );

        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.id()))
                    .name("message")
                    .data(event));
            return new SendResult(true, "메시지 전송 성공", event);
        } catch (IOException e) {
            emitters.remove(clientId);
            return new SendResult(false, "메시지 전송 실패: " + e.getMessage());
        }
    }

    // 연결된 클라이언트 목록 조회
    @GetMapping("/clients")
    public Map<String, String> getConnectedClients() {
        return Map.of(
                "count", String.valueOf(emitters.size()),
                "clients", String.join(", ", emitters.keySet())
        );
    }

    // 서버 시간 스트리밍
    @GetMapping(value = "/time", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTime() {
        SseEmitter emitter = new SseEmitter();

        new Thread(() -> {
            try {
                for (int i = 0; i < 60; i++) { // 60초 동안 실행
                    TimeEvent timeEvent = new TimeEvent(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            System.currentTimeMillis()
                    );
                    emitter.send(SseEmitter.event()
                            .name("time")
                            .data(timeEvent));
                    Thread.sleep(1000); // 1초 간격
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

}
