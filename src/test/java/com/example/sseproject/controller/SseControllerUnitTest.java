package com.example.sseproject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SseController.class)
class SseControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetClientsInitiallyEmpty() throws Exception {
        mockMvc.perform(get("/api/sse/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value("0"))
                .andExpect(jsonPath("$.clients").value(""));
    }

    @Test
    void testConnectAndVerifyClients() throws Exception {
        // 1. Connect a client
        MvcResult mvcResult = mockMvc.perform(get("/api/sse/connect").param("clientId", "test-client-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertNotNull(mvcResult.getAsyncResult());

        // 2. Verify that client is in the active list
        mockMvc.perform(get("/api/sse/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value("1"))
                .andExpect(jsonPath("$.clients").value("test-client-1"));
    }

    @Test
    void testBroadcastMessage() throws Exception {
        // First connect client to receive broadcast
        MvcResult mvcResult = mockMvc.perform(get("/api/sse/connect").param("clientId", "test-client-broadcast"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertNotNull(mvcResult.getAsyncResult());

        // Send broadcast
        String broadcastPayload = "{\"message\":\"Hello Broadcast!\"}";
        mockMvc.perform(post("/api/sse/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(broadcastPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failCount").value(0))
                .andExpect(jsonPath("$.event.message").value("Hello Broadcast!"))
                .andExpect(jsonPath("$.event.type").value("BROADCAST"));
    }

    @Test
    void testSendToSpecificClient() throws Exception {
        // First connect client
        MvcResult mvcResult = mockMvc.perform(get("/api/sse/connect").param("clientId", "test-client-direct"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertNotNull(mvcResult.getAsyncResult());

        // Send direct message
        String directPayload = "{\"message\":\"Hello Direct!\"}";
        mockMvc.perform(post("/api/sse/send/test-client-direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("메시지 전송 성공"))
                .andExpect(jsonPath("$.event.message").value("Hello Direct!"))
                .andExpect(jsonPath("$.event.type").value("DIRECT"));
    }

    @Test
    void testSendToNonExistentClient() throws Exception {
        String directPayload = "{\"message\":\"Hello Non-Existent!\"}";
        mockMvc.perform(post("/api/sse/send/no-such-client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("클라이언트를 찾을 수 없습니다: no-such-client"));
    }

    @Test
    void testStreamTime() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/sse/time"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertNotNull(mvcResult.getAsyncResult());
    }
}
