package xyz.crossplayproject;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import spark.Service;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class WebSocketHandler implements Listener {
    private final Gson gson = new Gson();
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Logger logger = Logger.getLogger(WebSocketHandler.class.getName());
    private final Set<Material> biomeSensitiveBlocks;
    private final JavaPlugin plugin;

    public WebSocketHandler(Set<Material> biomeSensitiveBlocks, JavaPlugin plugin) {
        this.biomeSensitiveBlocks = biomeSensitiveBlocks;
        this.plugin = plugin;
    }

    public void setupWebSocket(Service spark) {
        // WebSocket-Servlet für Live-Updates
        spark.webSocket("/live", new WebSocketAdapter() {
            @Override
            public void onWebSocketConnect(Session session) {
                sessions.add(session);
                logger.info("WebSocket client connected: " + session.getRemoteAddress());
                
                // Sende initiale Daten
                sendInitialData(session);
            }

            @Override
            public void onWebSocketClose(int statusCode, String reason) {
                sessions.remove(this.getSession());
                logger.info("WebSocket client disconnected: " + reason);
            }

            @Override
            public void onWebSocketText(String message) {
                try {
                    JsonObject request = gson.fromJson(message, JsonObject.class);
                    handleWebSocketMessage(request, this.getSession());
                } catch (Exception e) {
                    logger.warning("Error processing WebSocket message: " + e.getMessage());
                }
            }

            @Override
            public void onWebSocketError(Throwable cause) {
                logger.warning("WebSocket error: " + cause.getMessage());
                sessions.remove(this.getSession());
            }
        });

        // Regelmäßige Updates für Spieler und Welt
        scheduler.scheduleAtFixedRate(this::broadcastPlayerUpdates, 0, 100, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::broadcastWorldUpdates, 0, 2, TimeUnit.SECONDS);
    }

    private void sendInitialData(Session session) {
        if (!session.isOpen()) return;

        try {
            // Sende aktuelle Chunk-Daten
            JsonObject initialData = new JsonObject();
            initialData.addProperty("type", "initial");
            initialData.addProperty("message", "Connected to Minecraft server");
            
            session.getRemote().sendString(gson.toJson(initialData));
        } catch (IOException e) {
            logger.warning("Failed to send initial data: " + e.getMessage());
        }
    }

    private void handleWebSocketMessage(JsonObject request, Session session) {
        String type = request.get("type").getAsString();
        
        switch (type) {
            case "subscribe_chunks":
                handleChunkSubscription(request, session);
                break;
            case "heartbeat":
                sendHeartbeat(session);
                break;
            case "request_area":
                handleAreaRequest(request, session);
                break;
            default:
                logger.warning("Unknown WebSocket message type: " + type);
        }
    }

    private void handleChunkSubscription(JsonObject request, Session session) {
        try {
            int centerX = request.get("centerX").getAsInt();
            int centerZ = request.get("centerZ").getAsInt();
            int radius = request.get("radius").getAsInt();
            
            // Sende Chunk-Daten für den angeforderten Bereich
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                JsonObject response = new JsonObject();
                response.addProperty("type", "chunk_data");
                response.add("blocks", getChunkBlocks(centerX, centerZ, radius));
                
                try {
                    if (session.isOpen()) {
                        session.getRemote().sendString(gson.toJson(response));
                    }
                } catch (IOException e) {
                    logger.warning("Failed to send chunk data: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Error handling chunk subscription: " + e.getMessage());
        }
    }

    private void handleAreaRequest(JsonObject request, Session session) {
        try {
            int x1 = request.get("x1").getAsInt();
            int y1 = request.get("y1").getAsInt();
            int z1 = request.get("z1").getAsInt();
            int x2 = request.get("x2").getAsInt();
            int y2 = request.get("y2").getAsInt();
            int z2 = request.get("z2").getAsInt();
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                JsonObject response = new JsonObject();
                response.addProperty("type", "area_data");
                response.add("blocks", getAreaBlocks(x1, y1, z1, x2, y2, z2));
                
                try {
                    if (session.isOpen()) {
                        session.getRemote().sendString(gson.toJson(response));
                    }
                } catch (IOException e) {
                    logger.warning("Failed to send area data: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Error handling area request: " + e.getMessage());
        }
    }

    private void sendHeartbeat(Session session) {
        try {
            JsonObject heartbeat = new JsonObject();
            heartbeat.addProperty("type", "heartbeat");
            heartbeat.addProperty("timestamp", System.currentTimeMillis());
            
            if (session.isOpen()) {
                session.getRemote().sendString(gson.toJson(heartbeat));
            }
        } catch (IOException e) {
            logger.warning("Failed to send heartbeat: " + e.getMessage());
        }
    }

    private void broadcastPlayerUpdates() {
        if (sessions.isEmpty()) return;

        JsonObject playerUpdate = new JsonObject();
        playerUpdate.addProperty("type", "player_update");
        playerUpdate.add("players", getPlayerData());
        
        broadcast(gson.toJson(playerUpdate));
    }

    private void broadcastWorldUpdates() {
        if (sessions.isEmpty()) return;

        JsonObject worldUpdate = new JsonObject();
        worldUpdate.addProperty("type", "world_update");
        worldUpdate.add("world", getWorldData());
        
        broadcast(gson.toJson(worldUpdate));
    }

    private void broadcast(String message) {
        sessions.removeIf(session -> {
            try {
                if (session.isOpen()) {
                    session.getRemote().sendString(message);
                    return false;
                } else {
                    return true;
                }
            } catch (IOException e) {
                logger.warning("Failed to broadcast message: " + e.getMessage());
                return true;
            }
        });
    }

    // Event-Handler für Live-Updates
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (sessions.isEmpty()) return;

        Block block = event.getBlock();
        JsonObject blockUpdate = createBlockUpdate("place", block);
        broadcast(gson.toJson(blockUpdate));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (sessions.isEmpty()) return;

        Block block = event.getBlock();
        JsonObject blockUpdate = createBlockUpdate("break", block);
        broadcast(gson.toJson(blockUpdate));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (sessions.isEmpty()) return;

        JsonObject playerJoin = new JsonObject();
        playerJoin.addProperty("type", "player_join");
        playerJoin.addProperty("player", event.getPlayer().getName());
        playerJoin.addProperty("uuid", event.getPlayer().getUniqueId().toString());
        
        broadcast(gson.toJson(playerJoin));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (sessions.isEmpty()) return;

        JsonObject playerLeave = new JsonObject();
        playerLeave.addProperty("type", "player_leave");
        playerLeave.addProperty("player", event.getPlayer().getName());
        playerLeave.addProperty("uuid", event.getPlayer().getUniqueId().toString());
        
        broadcast(gson.toJson(playerLeave));
    }

    private JsonObject createBlockUpdate(String action, Block block) {
        JsonObject update = new JsonObject();
        update.addProperty("type", "block_update");
        update.addProperty("action", action);
        
        JsonObject blockData = new JsonObject();
        blockData.addProperty("x", block.getX());
        blockData.addProperty("y", block.getY());
        blockData.addProperty("z", block.getZ());
        blockData.addProperty("t", block.getType().toString());
        
        if (action.equals("place")) {
            BlockData data = block.getBlockData();
            String state = getFormattedState(data);
            if (state != null) {
                blockData.addProperty("s", state);
            }
            
            if (biomeSensitiveBlocks.contains(block.getType())) {
                blockData.addProperty("b", block.getBiome().toString());
            }
        }
        
        update.add("block", blockData);
        return update;
    }

    private String getFormattedState(BlockData blockData) {
        String fullState = blockData.getAsString();
        int startIndex = fullState.indexOf('[');
        if (startIndex != -1) {
            return fullState.substring(startIndex + 1, fullState.length() - 1);
        }
        return null;
    }

    // Hilfsmethoden für Datenabfrage
    private JsonObject getChunkBlocks(int centerX, int centerZ, int radius) {
        // Implementation für Chunk-Datenabfrage
        JsonObject result = new JsonObject();
        // TODO: Implementierung
        return result;
    }

    private JsonObject getAreaBlocks(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Implementation für Bereichs-Datenabfrage
        JsonObject result = new JsonObject();
        // TODO: Implementierung
        return result;
    }

    private JsonObject getPlayerData() {
        // Implementation für Spielerdaten
        JsonObject result = new JsonObject();
        // TODO: Implementierung
        return result;
    }

    private JsonObject getWorldData() {
        // Implementation für Weltdaten
        JsonObject result = new JsonObject();
        // TODO: Implementierung
        return result;
    }

    public void shutdown() {
        scheduler.shutdown();
        sessions.clear();
    }
}
