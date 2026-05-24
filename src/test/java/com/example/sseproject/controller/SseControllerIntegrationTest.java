package com.example.sseproject.controller;

import com.example.sseproject.dto.BroadcastResult;
import com.example.sseproject.dto.MessageRequest;
import com.example.sseproject.dto.SendResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SseControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testSseIntegrationFlow() throws Exception {
        String baseUrl = "http://localhost:" + port;
        String connectUrl = baseUrl + "/api/sse/connect?clientId=integ-client-1";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(connectUrl))
                .GET()
                .build();

        // 1. Connect asynchronously
        CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        // Wait a short time for the connection to establish and register on the server
        Thread.sleep(1000);

        // 2. Verify that client is in the active list using TestRestTemplate
        ResponseEntity<Map> clientsResponse = restTemplate.getForEntity("/api/sse/clients", Map.class);
        assertEquals(200, clientsResponse.getStatusCode().value());
        Map<String, Object> clientsBody = clientsResponse.getBody();
        assertNotNull(clientsBody);
        assertEquals("1", clientsBody.get("count"));
        assertTrue(((String) clientsBody.get("clients")).contains("integ-client-1"));

        // 3. Send a direct message to our connected client
        MessageRequest msgRequest = new MessageRequest("Integration Hello");
        ResponseEntity<SendResult> sendResponse = restTemplate.postForEntity("/api/sse/send/integ-client-1", msgRequest, SendResult.class);
        assertEquals(200, sendResponse.getStatusCode().value());
        SendResult sendResult = sendResponse.getBody();
        assertNotNull(sendResult);
        assertTrue(sendResult.success());
        assertEquals("메시지 전송 성공", sendResult.message());
        assertEquals("Integration Hello", sendResult.event().message());

        // 4. Broadcast a message to all clients
        MessageRequest broadcastMsg = new MessageRequest("Integration Broadcast");
        ResponseEntity<BroadcastResult> broadcastResponse = restTemplate.postForEntity("/api/sse/broadcast", broadcastMsg, BroadcastResult.class);
        assertEquals(200, broadcastResponse.getStatusCode().value());
        BroadcastResult broadcastResult = broadcastResponse.getBody();
        assertNotNull(broadcastResult);
        assertEquals(1, broadcastResult.successCount());
        assertEquals(0, broadcastResult.failCount());
        assertEquals("Integration Broadcast", broadcastResult.event().message());

        // Now cancel or complete the async connection request to clean up
        responseFuture.cancel(true);
    }
}
