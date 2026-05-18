package com.wallrunner.server.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.server.dto.NetworkMessage;
import com.wallrunner.server.service.DedicatedService;
import com.wallrunner.server.service.RelayService;
import com.wallrunner.server.service.RoomManager;
import com.wallrunner.server.service.SessionManager;
import com.wallrunner.shared.constants.GameConstants;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

/**
 * 【模块】server / handler
 * 【代号】X
 * 【职责】WebSocket 消息总线入口。与原始网页版服务端协议严格对齐。
 * 【协议】mode_select | input | state
 * 【原则】仅做"交通警察"，不执行游戏逻辑（委托给 Service）。
 * 【修复】2026-05-10:
 *       1. 优先使用客户端提供的 clientId 作为 playerId，实现客户端身份持久化。
 * 【修复】2026-05-11:
 *       1. 颜色分配：使用 PLAYER_COLOR_PAIRS，确保同一房间内颜色不重复。
 *       2. 支持客户端自定义颜色（fillColor + strokeColor），默认时分配随机不重复颜色。
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
        } else if ("ping".equals(type)) {
            handlePing(session, msg);
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

        // 【修复】颜色处理：客户端自定义 > 服务器分配不重复随机色
        String clientFill = (String) msg.get("fillColor");
        String clientStroke = (String) msg.get("strokeColor");
        if (clientFill != null && !clientFill.isEmpty() && clientStroke != null && !clientStroke.isEmpty()) {
            // 客户端提供了自定义颜色
            player.setFillColor(clientFill);
            player.setStrokeColor(clientStroke);
            player.setColor(clientFill); // 兼容旧字段
        } else {
            // 服务器分配不重复颜色
            assignUniqueColor(player, roomId, mode, role);
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
                String customRoomId = (String) msg.get("roomId");
                String rid;
                if (customRoomId != null && !customRoomId.isEmpty()) {
                    rid = relayService.createRoom(session.getId(), customRoomId.toUpperCase());
                    if (rid == null) {
                        sendError(session, "房间号已被使用，请更换");
                        return;
                    }
                } else {
                    rid = relayService.createRoom(session.getId());
                }
                relayService.joinRoom(rid, player, session);
                sessionManager.bindRoom(session.getId(), rid);
                reply(session, Map.of("type", "room_created", "roomId", rid,
                        "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()));
            } else if ("join".equals(role)) {
                if (roomId == null || roomId.isEmpty()) {
                    sendError(session, "房间号不能为空");
                    return;
                }
                boolean ok = relayService.joinRoom(roomId, player, session);
                if (ok) {
                    sessionManager.bindRoom(session.getId(), roomId);
                    reply(session, Map.of("type", "joined_room", "roomId", roomId, "playerId", clientId,
                            "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()));
                    relayService.broadcastToRoom(roomId, Map.of("type", "player_joined", "playerId", clientId, "name", name,
                            "fillColor", player.getFillColor(), "strokeColor", player.getStrokeColor()), session.getId());
                } else {
                    sendError(session, "房间不存在或已关闭");
                }
            }
        }
    }

    /**
     * 为玩家分配与其他玩家均不重复的颜色。
     * Dedicated 模式从全局已用颜色中排除；Relay 模式从房间内已用颜色中排除。
     */
    private void assignUniqueColor(Player player, String roomId, String mode, String role) {
        Set<String> usedFills = new HashSet<>();

        // 收集已用颜色
        if ("dedicated".equals(mode)) {
            // Dedicated 单房间：从该房间收集
            String dedRoom = "DEDICATED-MAIN";
            var state = roomManager.getRoom(dedRoom);
            if (state != null) {
                for (Player p : state.getPlayers().values()) {
                    usedFills.add(p.getFillColor());
                }
            }
        } else if ("relay".equals(mode) && "join".equals(role)) {
            // Relay 加入：从目标房间收集
            var state = roomManager.getRoom(roomId);
            if (state != null) {
                for (Player p : state.getPlayers().values()) {
                    usedFills.add(p.getFillColor());
                }
            }
        } else if ("relay".equals(mode) && "create".equals(role)) {
            // Relay 创建房间：新房，无已用颜色
        }

        // 分配第一个未使用的颜色，若全部用完则随机分配
        String[][] pairs = GameConstants.PLAYER_COLOR_PAIRS;
        for (String[] pair : pairs) {
            if (!usedFills.contains(pair[0])) {
                player.setFillColor(pair[0]);
                player.setStrokeColor(pair[1]);
                player.setColor(pair[0]);
                return;
            }
        }
        // 所有颜色都被占用，随机分配
        int idx = (int) (Math.random() * pairs.length);
        player.setFillColor(pairs[idx][0]);
        player.setStrokeColor(pairs[idx][1]);
        player.setColor(pairs[idx][0]);
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

    private void handlePing(WebSocketSession session, Map<String, Object> msg) {
        String roomId = sessionManager.getRoomId(session.getId());
        if (roomId == null) return;
        String clientId = (String) msg.get("clientId");
        if (clientId == null) return;
        GameState state = roomManager.getRoom(roomId);
        if (state != null) {
            Player p = state.getPlayers().get(clientId);
            if (p != null) {
                p.setLastPingTime(System.currentTimeMillis());
                p.setDisconnected(false);
            }
        }
        // 回复 pong
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "pong"))));
        } catch (Exception ignored) {}
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
