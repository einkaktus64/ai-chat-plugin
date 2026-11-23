package dev.aimob.service;

import dev.aimob.AiMobPlugin;
import dev.aimob.ai.AiProvider;
import dev.aimob.ai.OllamaProvider;
import dev.aimob.ai.OpenAiProvider;
import dev.aimob.util.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class ChatService {
    private static final class Session {
        private record MobPosition(UUID worldId, double x, double y, double z) { }
        final String mobId;
        final String mobName;
        final UUID playerId;
        final Player player;
        final MobManager.MobRecord mob;
        volatile MobPosition mobPosition;
        volatile ScheduledTask positionTask;
        volatile long lastActivity;
        Session(MobManager.MobRecord mob, Player player, UUID playerId) {
            mobId = mob.id(); mobName = mob.name(); this.mob = mob;
            this.player = player; this.playerId = playerId;
            lastActivity = System.currentTimeMillis();
        }
        boolean tooFar(Location location, double distance) {
            MobPosition position = mobPosition;
            if (position == null) return false;
            if (!location.getWorld().getUID().equals(position.worldId())) return true;
            double dx = location.getX() - position.x();
            double dy = location.getY() - position.y();
            double dz = location.getZ() - position.z();
            return dx * dx + dy * dy + dz * dz > distance * distance;
        }
    }

    private final AiMobPlugin plugin;
    private final MobManager mobs;
    private final Messages messages;
    private final DialogChatUi dialogUi;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<AiProvider.ChatMessage>> histories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final AtomicInteger activeRequests = new AtomicInteger();
    private volatile long timeoutMillis;
    private volatile boolean clearHistoryOnEnd;
    private volatile int maxHistoryMessages;

    public ChatService(AiMobPlugin plugin, MobManager mobs, Messages messages) {
        this.plugin = plugin; this.mobs = mobs; this.messages = messages;
        this.dialogUi = new DialogChatUi(plugin, this);
        refreshSettings();
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> checkTimeouts(), 1, 1,
                java.util.concurrent.TimeUnit.SECONDS);
    }

    public void refreshSettings() {
        timeoutMillis = Math.max(1, plugin.getConfig().getLong("chat.timeout-seconds", 120)) * 1000L;
        clearHistoryOnEnd = plugin.getConfig().getBoolean("chat.clear-history-on-session-end", true);
        maxHistoryMessages = Math.max(2, plugin.getConfig().getInt("max-history-messages", 12));
    }

    public void open(Player player, MobManager.MobRecord mob) {
        String mode = plugin.getConfig().getString("chat.ui-mode", "chat");
        UUID playerId = player.getUniqueId();
        Session session = new Session(mob, player, playerId);
        Session old = sessions.put(player.getUniqueId(), session);
        if (old != null) {
            if (old.positionTask != null) old.positionTask.cancel();
            if (clearHistoryOnEnd) histories.remove(historyKey(player.getUniqueId(), old.mobId));
        }
        session.positionTask = mob.entity().getScheduler().runAtFixedRate(plugin, task -> {
            if (sessions.get(session.playerId) != session) { task.cancel(); return; }
            Location current = mob.entity().getLocation();
            session.mobPosition = new Session.MobPosition(current.getWorld().getUID(), current.getX(), current.getY(), current.getZ());
        }, () -> end(session.playerId, "chat-exit", null), 1L, 20L);
        player.sendMessage(messages.prefixed("chat-start", Map.of("mob_name", MiniMessage.miniMessage().escapeTags(mob.name()),
                "exit_word", plugin.getConfig().getString("chat.exit-word", "exit"))));
        if (mode.equalsIgnoreCase("dialog") && !dialogUi.show(player, mob.name())) {
            player.sendMessage(messages.component("dialog-fallback"));
        }
    }

    public boolean isActive(UUID playerId) { return sessions.containsKey(playerId); }

    public void onChat(Player player, String message) {
        UUID playerId = player.getUniqueId();
        Session session = sessions.get(playerId);
        if (session == null) return;
        if (!player.hasPermission("aimob.chat")) {
            end(playerId, "chat-exit", player);
            player.sendMessage(messages.component("chat-no-permission"));
            return;
        }
        String exitWord = plugin.getConfig().getString("chat.exit-word", "exit");
        if (message.trim().equalsIgnoreCase(exitWord)) {
            end(playerId, "chat-exit", player);
            return;
        }
        int maxLength = Math.max(1, plugin.getConfig().getInt("max-message-length", 500));
        if (message.length() > maxLength) { send(player, messages.component("chat-too-long")); return; }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, plugin.getConfig().getLong("message-cooldown-seconds", 2)) * 1000L;
        Long previous = cooldowns.putIfAbsent(playerId, now);
        if (previous != null && now - previous < cooldown) { send(player, messages.component("chat-cooldown")); return; }
        cooldowns.put(playerId, now);
        int limit = Math.max(1, plugin.getConfig().getInt("max-concurrent-requests", 4));
        if (activeRequests.incrementAndGet() > limit) {
            activeRequests.decrementAndGet(); send(player, messages.component("chat-busy")); return;
        }
        session.lastActivity = now;
        String key = historyKey(playerId, session.mobId);
        CopyOnWriteArrayList<AiProvider.ChatMessage> history = histories.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        history.add(new AiProvider.ChatMessage("user", message));
        trimHistory(history);
        List<AiProvider.ChatMessage> request = new ArrayList<>();
        String system = plugin.getConfig().getString("system-prompt", "You are a friendly character named <mob_name>.")
                .replace("<mob_name>", session.mobName);
        request.add(new AiProvider.ChatMessage("system", system));
        request.addAll(history);
        send(player, messages.component("chat-thinking", Map.of("mob_name", MiniMessage.miniMessage().escapeTags(session.mobName))));
        AiProvider provider = createProvider();
        CompletableFuture<String> future;
        try { future = provider.complete(request); }
        catch (RuntimeException exception) { activeRequests.decrementAndGet(); handleFailure(player, exception); return; }
        future.whenComplete((answer, error) -> {
            activeRequests.decrementAndGet();
            if (error != null) {
                if (sessions.get(playerId) == session) handleFailure(player, error);
                return;
            }
            if (sessions.get(playerId) != session) return;
            history.add(new AiProvider.ChatMessage("assistant", answer));
            trimHistory(history);
            String safeAnswer = MiniMessage.miniMessage().escapeTags(answer);
            sendKey(player, "chat-reply", Map.of(
                    "mob_name", MiniMessage.miniMessage().escapeTags(session.mobName), "message", safeAnswer));
        });
    }

    private AiProvider createProvider() {
        String provider = plugin.getConfig().getString("provider", "openai");
        if (provider.equalsIgnoreCase("ollama")) return new OllamaProvider(plugin);
        if (!provider.equalsIgnoreCase("openai")) plugin.getLogger().warning("Unknown provider '" + provider + "'; using OpenAI-compatible mode.");
        return new OpenAiProvider(plugin);
    }

    private void trimHistory(List<AiProvider.ChatMessage> history) {
        while (history.size() > maxHistoryMessages) history.remove(0);
    }

    private void handleFailure(Player player, Throwable error) {
        Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
        plugin.getLogger().warning("AI request failed: " + cause.getMessage());
        sendKey(player, "chat-error", Map.of());
    }

    private void send(Player player, Component component) {
        player.getScheduler().run(plugin, task -> player.sendMessage(component), null);
    }

    private void sendKey(Player player, String key, Map<String, String> replacements) {
        player.getScheduler().run(plugin, task -> player.sendMessage(messages.component(key, replacements)), null);
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        sessions.forEach((playerId, session) -> {
            if (now - session.lastActivity >= timeoutMillis) end(playerId, "chat-timeout", null);
        });
    }

    public void checkDistance(Player player, Location location) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.tooFar(location, plugin.getConfig().getDouble("chat.max-distance", 16.0))) {
            end(player.getUniqueId(), "chat-distance", player);
        }
    }

    public void logout(Player player) { end(player.getUniqueId(), "chat-logout", null); }

    private void end(UUID playerId, String messageKey, Player knownPlayer) {
        Session session = sessions.remove(playerId);
        if (session == null) return;
        if (session.positionTask != null) session.positionTask.cancel();
        if (clearHistoryOnEnd) histories.remove(historyKey(playerId, session.mobId));
        if (knownPlayer != null) sendKey(knownPlayer, messageKey, Map.of());
        else if (!messageKey.equals("chat-logout")) sendKey(session.player, messageKey, Map.of());
    }

    public void endSessionsForMob(String mobId) {
        sessions.forEach((playerId, session) -> {
            if (session.mobId.equals(mobId)) end(playerId, "chat-exit", null);
        });
    }

    private String historyKey(UUID player, String mob) { return player + ":" + mob; }

    public void shutdown() {
        sessions.clear();
        histories.clear();
    }
}
