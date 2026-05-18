package com.wallrunner.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.server.dto.NetworkMessage;
import com.wallrunner.server.service.DedicatedService;
import com.wallrunner.server.service.RelayService;
import com.wallrunner.server.service.RoomManager;
import com.wallrunner.server.service.SessionManager;
import com.wallrunner.shared.entity.Player;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 【模块】server / handler
 * 【代号】X
 * 【职责】WebSocket 消息总线入口。与原始网页版服务端协议严格对齐。
 * 【协议】mode_select | input | state
 * 【原则】仅做"交通警察"，不执行游戏逻辑（委托给 Service）。
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionManager sessionManager;
    private final RoomManager roomManager;
    private final DedicatedService dedicatedService;
    private final RelayService relayService;

    public GameWebSocketHandler(SessionManager sessionManager,
                                RoomManager roomManager,
                                DedicatedService dedicatedService,
                                RelayService relayService) {
        this.sessionManager = sessionManager;
        this.roomManager = roomManager;
        this.dedicatedService = dedicatedService;
        this.relayService = relayService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.register(session);
        System.out.println("[WS] Connected: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        relayService.leave(session.getId());
        sessionManager.unregister(session.getId());
        System.out.println("[WS] Disconnected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            route(session, msg);
        } catch (Exception e) {
            System.err.println("[WS] Parse error from " + session.getId() + ": " + e.getMessage());
            sendError(session, "Invalid message format");
        }
    }

    private void route(WebSocketSession session, Map<String, Object> msg) {
        String type = (String) msg.get("type");
        if ("mode_select".equals(type)) {
            handleModeSelect(session, msg);
        } else if ("input".equals(type)) {
            handleInput(session, msg);
        } else if ("state".equals(type)) {
            handleStateForward(session, msg);
        }
    }

    private void handleModeSelect(WebSocketSession session, Map<String, Object> msg) {
        String mode = (String) msg.get("mode");
        String name = (String) msg.getOrDefault("name", "玩家");
        String role = (String) msg.get("role");
        String roomId = (String) msg.get("roomId");

        Player player = new Player(session.getId(), name);
        player.setColor(com.wallrunner.shared.constants.GameConstants.PLAYER_COLORS[Math.abs(session.getId().hashCode()) % com.wallrunner.shared.constants.GameConstants.PLAYER_COLORS.length]);

        if ("dedicated".equals(mode)) {
            String dedRoomId = dedicatedService.getOrCreateRoom();
            dedicatedService.join(dedRoomId, player, session);
            sessionManager.bindRoom(session.getId(), dedRoomId);
            reply(session, Map.of("type", "mode_confirmed", "mode", "dedicated", "playerId", session.getId()));
        } else if ("relay".equals(mode)) {
            if ("create".equals(role)) {
                String rid = relayService.createRoom(session.getId());
                relayService.joinRoom(rid, player, session);
                sessionManager.bindRoom(session.getId(), rid);
                reply(session, Map.of("type", "room_created", "roomId", rid));
            } else if ("join".equals(role)) {
                if (roomId == null || roomId.isEmpty()) {
                    sendError(session, "房间号不能为空");
                    return;
                }
                boolean ok = relayService.joinRoom(roomId, player, session);
                if (ok) {
                    sessionManager.bindRoom(session.getId(), roomId);
                    reply(session, Map.of("type", "joined_room", "roomId", roomId, "playerId", session.getId()));
                    // 通知房主
                    relayService.notifyHost(roomId, Map.of("type", "player_joined", "playerId", session.getId(), "name", name));
                } else {
                    sendError(session, "房间不存在或已关闭");
                }
            }
        }
    }

    private void handleInput(WebSocketSession session, Map<String, Object> msg) {
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId == null) return;
        String action = (String) msg.get("action");
        String playerId = (String) msg.getOrDefault("playerId", session.getId());

        if (roomId.startsWith("DEDICATED-")) {
            dedicatedService.handleInput(roomId, playerId, action);
        } else {
            relayService.relayInput(roomId, msg, session);
        }
    }

    private void handleStateForward(WebSocketSession session, Map<String, Object> msg) {
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId == null || roomId.startsWith("DEDICATED-")) return;
        relayService.broadcastFromHost(roomId, msg, session);
    }

    private void reply(WebSocketSession session, Map<String, Object> msg) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            System.err.println("[WS] Reply failed: " + e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String error) {
        reply(session, Map.of("type", "error", "message", error));
    }
}
