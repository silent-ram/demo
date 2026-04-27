package com.example.alertservice.handler;

import com.example.alertservice.entity.Alert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Message received: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error", exception);
    }

    public void broadcastAlert(Alert alert) {
        int openCount = 0;
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) openCount++;
        }
        log.info("Broadcasting alert {} to {} open sessions (total {})", alert.getId(), openCount, sessions.size());
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("id", alert.getId());
                    payload.put("deviceId", alert.getDeviceId());
                    payload.put("deviceName", alert.getDeviceName() != null ? alert.getDeviceName() : "");
                    payload.put("faultProbability", alert.getFaultProbability() != null ? alert.getFaultProbability().toString() : "0");
                    payload.put("alertLevel", alert.getAlertLevel() != null ? alert.getAlertLevel() : "MEDIUM");
                    payload.put("type", alert.getType() != null ? alert.getType() : "");
                    payload.put("message", alert.getMessage() != null ? alert.getMessage() : "");
                    payload.put("resolved", false);
                    payload.put("createdAt", alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : "");
                    String json = objectMapper.writeValueAsString(payload);
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    log.error("Error sending message to session: {}", session.getId(), e);
                }
            }
        }
    }
}
