package com.wallrunner.server.handler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.server.service.DedicatedService;
import com.wallrunner.server.service.RelayService;
import com.wallrunner.server.service.RoomManager;
import com.wallrunner.server.service.SessionManager;
import com.wallrunner.shared.constants.GameConstants;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

/**
 * WebSocket 消息总线入口。
 *
 * 职责：仅做消息路由（"交通警察"），不执行游戏逻辑（委托给 Service）。
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId != null) {
            // 统一处理所有模式（Dedicated/Relay）的离开逻辑
            roomManager.leaveRoom(roomId, session.getId());
            sessionManager.unregister(session.getId());
        }
        System.out.println("[WS] Disconnected: " + session.getId());
    }

    @Override
    @SuppressWarnings("UseSpecificCatch")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
            route(session, msg);
        } catch (Exception e) {
            System.err.println("[WS] Parse error from " + session.getId() + ": " + e.getMessage());
            sendError(session, "Invalid message format");
        }
    }

    private void route(WebSocketSession session, Map<String, Object> msg) {
        String type = (String) msg.get("type");
        if (null != type) switch (type) {
            case "mode_select" -> handleModeSelect(session, msg);
            case "input" -> handleInput(session, msg);
            case "state" -> handleStateForward(session, msg);
            case "ping" -> handlePing(session, msg);
            case "pong" -> handlePong(session, msg);
            case "disconnect" -> handleClientDisconnect(session, msg);
            default -> {
            }
        }
    }

    private void handleModeSelect(WebSocketSession session, Map<String, Object> msg) {
        String mode = (String) msg.get("mode");
        String name = (String) msg.getOrDefault("name", "玩家");
        String role = (String) msg.get("role");
        String roomId = (String) msg.get("roomId");
        String clientId = (String) msg.get("clientId");
        if (clientId == null || clientId.isEmpty()) {
            clientId = session.getId();
        }

        Player player = new Player(clientId, name);

        // 颜色处理：客户端自定义 > 服务器分配不重复随机色
        String clientFill = (String) msg.get("fillColor");
        String clientStroke = (String) msg.get("strokeColor");
        if (clientFill != null && !clientFill.isEmpty() && clientStroke != null && !clientStroke.isEmpty()) {
            player.setFillColor(clientFill);
            player.setStrokeColor(clientStroke);
        } else {
            assignUniqueColor(player, roomId);
        }

        if ("dedicated".equals(mode)) {
            String dedRoomId = dedicatedService.getOrCreateRoom();
            boolean isLateJoin = dedicatedService.isRoomActive(dedRoomId);
            dedicatedService.join(dedRoomId, player, session);
            sessionManager.bindRoom(session.getId(), dedRoomId);
            reply(session, Map.of("type", "mode_confirmed", "mode", "dedicated", "playerId", clientId,
                    "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()));
            if (isLateJoin) {
                broadcastToRoom(dedRoomId, Map.of("type", "player_joined", "playerId", clientId, "name", name,
                        "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()), session.getId());
            }
        } else if ("relay".equals(mode)) {
            if ("create".equals(role)) {
                String rid = relayService.createRoom(session.getId(), roomId);
                if (rid == null) {
                    sendError(session, "房间码已被使用");
                    return;
                }
                relayService.joinRoom(rid, player, session);
                reply(session, Map.of("type", "room_created", "roomId", rid,
                        "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()));
                broadcastToRoom(rid, Map.of("type", "player_joined", "playerId", clientId, "name", name,
                        "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()), session.getId());
            } else if ("join".equals(role)) {
                if (roomId == null || roomId.isEmpty()) {
                    sendError(session, "请输入房间号");
                    return;
                }
                if (!roomManager.isRoomExists(roomId)) {
                    sendError(session, "房间不存在");
                    return;
                }
                boolean ok = relayService.joinRoom(roomId, player, session);
                if (!ok) {
                    sendError(session, "加入房间失败");
                    return;
                }
                reply(session, Map.of("type", "joined_room", "roomId", roomId, "playerId", clientId,
                        "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()));
                broadcastToRoom(roomId, Map.of("type", "player_joined", "playerId", clientId, "name", name,
                        "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()), session.getId());
            }
        }
    }

    private void handleInput(WebSocketSession session, Map<String, Object> msg) {
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId == null) return;
        String action = (String) msg.get("action");
        String playerId = (String) msg.getOrDefault("playerId", session.getId());

        GameState state = roomManager.getRoom(roomId);
        if (state == null) return;
        String hostId = roomManager.getHost(roomId);
        boolean isHost = session.getId().equals(hostId);

        if (roomId.startsWith("DEDICATED")) {
            dedicatedService.handleInput(roomId, playerId, action);
        } else if (isHost) {
            // 房主本地处理输入，由房主客户端广播状态
        } else {
            relayService.relayInput(roomId, msg, session);
        }
    }

    private void handleStateForward(WebSocketSession session, Map<String, Object> msg) {
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId == null) return;
        String hostId = roomManager.getHost(roomId);
        if (hostId != null && hostId.equals(session.getId())) {
            relayService.broadcastFromHost(roomId, msg, session);
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    private void handlePing(WebSocketSession session, Map<String, Object> msg) {
        String clientId = (String) msg.get("clientId");
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId != null && clientId != null) {
            GameState state = roomManager.getRoom(roomId);
            if (state != null) {
                Player p = state.getPlayers().get(clientId);
                if (p != null) {
                    p.setLastPingTime(System.currentTimeMillis());
                    p.setDisconnected(false);
                }
            }
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "pong"))));
        } catch (Exception ignored) {}
    }

    private void assignUniqueColor(Player player, String roomId) {
        Set<String> usedColors = new HashSet<>();
        if (roomId != null && roomManager.isRoomExists(roomId)) {
            GameState state = roomManager.getRoom(roomId);
            if (state != null) {
                for (Player p : state.getPlayers().values()) {
                    if (p.getFillColor() != null) usedColors.add(p.getFillColor());
                }
            }
        }
        for (String[] pair : GameConstants.PLAYER_COLOR_PAIRS) {
            if (!usedColors.contains(pair[0])) {
                player.setFillColor(pair[0]);
                player.setStrokeColor(pair[1]);
                return;
            }
        }
        //  fallback: 随机分配
        int idx = Math.abs(player.getId().hashCode()) % GameConstants.PLAYER_COLOR_PAIRS.length;
        player.setFillColor(GameConstants.PLAYER_COLOR_PAIRS[idx][0]);
        player.setStrokeColor(GameConstants.PLAYER_COLOR_PAIRS[idx][1]);
    }

    @SuppressWarnings("UseSpecificCatch")
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

    private void handlePong(WebSocketSession session, Map<String, Object> msg) {
        String playerId = (String) msg.get("playerId");
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId == null) roomId = (String) msg.get("roomId");
        if (roomId != null && playerId != null) {
            GameState state = roomManager.getRoom(roomId);
            if (state != null) {
                Player p = state.getPlayers().get(playerId);
                if (p != null) {
                    p.setLastPingTime(System.currentTimeMillis());
                    p.setPingAcknowledged(true);
                }
            }
        }
    }

    private void handleClientDisconnect(WebSocketSession session, Map<String, Object> msg) {
        String playerId = (String) msg.get("playerId");
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId == null) roomId = (String) msg.get("roomId");
        if (roomId != null && playerId != null) {
            GameState state = roomManager.getRoom(roomId);
            if (state != null) {
                Player p = state.getPlayers().get(playerId);
                if (p != null) {
                    long now = System.currentTimeMillis();
                    p.setDisconnected(true);
                    p.setOfflineTime(now);
                    p.setPaused(true);
                    System.out.println("[WS Handler] Player " + p.getName() + " disconnected (returned to menu)");
                }
            }
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    private void broadcastToRoom(String roomId, Map<String, Object> msg, String excludeSessionId) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            TextMessage tm = new TextMessage(json);
            for (WebSocketSession s : sessionManager.getAllSessions().values()) {
                if (roomId.equals(sessionManager.getRoomId(s.getId())) && s.isOpen() && !s.getId().equals(excludeSessionId)) {
                    s.sendMessage(tm);
                }
            }
        } catch (Exception e) {
            System.err.println("[WS] Broadcast failed: " + e.getMessage());
        }
    }
}
