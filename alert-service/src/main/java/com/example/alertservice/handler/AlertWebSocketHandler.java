package com.example.alertservice.handler;

import com.example.alertservice.entity.Alert;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("WebSocket connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("Message received: " + message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("WebSocket disconnected: " + session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket transport error: " + exception.getMessage());
    }

    public void broadcastAlert(Alert alert) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    // 发送JSON格式的告警信息
                    String json = String.format(
                        "{\"id\":%d,\"deviceId\":%d,\"deviceName\":\"%s\",\"faultProbability\":%s,\"alertLevel\":\"%s\",\"type\":\"%s\",\"message\":\"%s\",\"resolved\":false,\"createdAt\":\"%s\"}",
                        alert.getId(),
                        alert.getDeviceId(),
                        alert.getDeviceName() != null ? alert.getDeviceName() : "",
                        alert.getFaultProbability() != null ? alert.getFaultProbability().toString() : "0",
                        alert.getAlertLevel() != null ? alert.getAlertLevel() : "MEDIUM",
                        alert.getType() != null ? alert.getType() : "",
                        alert.getMessage() != null ? alert.getMessage().replace("\"", "\\\"") : "",
                        alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : ""
                    );
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    System.err.println("Error sending message to session: " + e.getMessage());
                }
            }
        }
    }
}