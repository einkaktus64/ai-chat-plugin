package dev.aimob.listener;

import dev.aimob.AiMobPlugin;
import dev.aimob.service.ChatService;
import dev.aimob.service.MobManager;
import dev.aimob.util.Messages;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

public final class MobListener implements Listener {
    private final AiMobPlugin plugin;
    private final MobManager mobs;
    private final ChatService chat;
    private final Messages messages;
    private final ConcurrentHashMap<UUID, GameMode> gameModes = new ConcurrentHashMap<>();

    public MobListener(AiMobPlugin plugin, MobManager mobs, ChatService chat, Messages messages) {
        this.plugin = plugin; this.mobs = mobs; this.chat = chat; this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) mobs.recognize(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) mobs.forget(entity);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!mobs.isProtected(event.getEntity())) return;
        Entity damager = event.getDamager();
        UUID attackerId = damager instanceof Player player ? player.getUniqueId()
                : damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player.getUniqueId() : null;
        if (attackerId == null) return;
        String mode = plugin.getConfig().getString("protection.mode", "survival-only");
        if (mode.equalsIgnoreCase("all-players") || gameModes.getOrDefault(attackerId, GameMode.SURVIVAL) != GameMode.CREATIVE) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (mobs.isProtected(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttackPush(EntityPushedByEntityAttackEvent event) {
        if (mobs.isProtected(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (mobs.isProtected(event.getMount()) || mobs.isProtected(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (mobs.isProtected(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnleash(EntityUnleashEvent event) {
        if (mobs.isProtected(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        MobManager.MobRecord mob = mobs.recognize(event.getRightClicked());
        if (mob == null) return;
        event.setCancelled(true); // Stops name-tag edits and other vanilla interactions with this entity.
        Player player = event.getPlayer();
        if (mobs.isRemovalStick(player.getInventory().getItemInMainHand())
                || mobs.isRemovalStick(player.getInventory().getItemInOffHand())) {
            if (!player.isSneaking()) return;
            if (!player.hasPermission("aimob.admin")) {
                player.sendMessage(messages.prefixed("no-permission"));
                return;
            }
            if (mobs.remove(mob)) {
                player.sendMessage(messages.prefixed("mob-removed-stick", Map.of("mob_name", MiniMessage.miniMessage().escapeTags(mob.name()))));
            }
            return;
        }
        if (!player.hasPermission("aimob.chat")) {
            player.sendMessage(messages.prefixed("chat-no-permission"));
            return;
        }
        chat.open(player, mob);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (!chat.isActive(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Player player = event.getPlayer();
        // AsyncChatEvent is asynchronous; process player and configuration state on its owning region.
        player.getScheduler().run(plugin, task -> chat.onChat(player, plain), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ())) {
            chat.checkDistance(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { chat.logout(event.getPlayer()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { gameModes.put(event.getPlayer().getUniqueId(), event.getPlayer().getGameMode()); }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        gameModes.put(event.getPlayer().getUniqueId(), event.getNewGameMode());
    }

    @EventHandler
    public void onQuitMode(PlayerQuitEvent event) { gameModes.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onDeath(EntityDeathEvent event) { mobs.forget(event.getEntity()); }
}
