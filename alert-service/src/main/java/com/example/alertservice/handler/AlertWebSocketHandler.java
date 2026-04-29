package com.example.alertservice.handler;

import com.example.alertservice.config.JwtUtil;
import com.example.alertservice.entity.Alert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // WebSocket 握手认证：从 URL 查询参数提取 token
        URI uri = session.getUri();
        String token = extractTokenFromUri(uri);

        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("WebSocket authentication failed for session: {}, uri: {}", session.getId(), uri);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing or invalid token"));
            return;
        }

        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        // 将用户信息存入 session attributes，后续可使用
        session.getAttributes().put("username", username);
        session.getAttributes().put("role", role);

        sessions.add(session);
        log.info("WebSocket connected: {} (user: {}, role: {})", session.getId(), username, role);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Message received from session {}: {}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket disconnected: {} (status: {})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: {}", session.getId(), exception);
    }

    /**
     * 广播告警给所有已认证的 WebSocket 客户端
     */
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

    /**
     * 从 WebSocket URI 的查询参数中提取 token
     */
    private String extractTokenFromUri(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && "token".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
