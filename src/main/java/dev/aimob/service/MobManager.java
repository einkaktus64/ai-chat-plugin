package dev.aimob.service;

import dev.aimob.AiMobPlugin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class MobManager {
    public record MobRecord(String id, String name, UUID entityUuid, Entity entity) { }
    private final AiMobPlugin plugin;
    private final ConcurrentHashMap<String, MobRecord> byId = new ConcurrentHashMap<>();

    public MobManager(AiMobPlugin plugin) { this.plugin = plugin; }

    public MobRecord spawn(Player player, String requestedName) {
        String name = requestedName == null || requestedName.isBlank()
                ? plugin.getConfig().getString("mob.default-name", "Guide") : requestedName;
        EntityType type;
        try { type = EntityType.valueOf(plugin.getConfig().getString("mob.type", "VILLAGER").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return null; }
        Location location = player.getLocation();
        Entity spawned = location.getWorld().spawnEntity(location, type);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            return null;
        }
        String id = UUID.randomUUID().toString();
        configure(living, name);
        living.getPersistentDataContainer().set(plugin.mobIdKey(), PersistentDataType.STRING, id);
        living.getPersistentDataContainer().set(plugin.mobNameKey(), PersistentDataType.STRING, name);
        MobRecord record = new MobRecord(id, name, living.getUniqueId(), living);
        byId.put(id, record);
        return record;
    }

    private void configure(LivingEntity living, String name) {
        var config = plugin.getConfig();
        living.customName(MiniMessage.miniMessage().deserialize(config.getString("mob.custom-name", "<gold><mob_name></gold>")
                .replace("<mob_name>", MiniMessage.miniMessage().escapeTags(name))));
        living.setCustomNameVisible(config.getBoolean("mob.name-visible", true));
        living.setGlowing(config.getBoolean("mob.glowing", false));
        living.setAI(!config.getBoolean("mob.noai", true));
        living.setGravity(config.getBoolean("mob.gravity", false));
        living.setCollidable(false);
        living.setPersistent(true);
        living.setRemoveWhenFarAway(false);
        living.setSilent(config.getBoolean("mob.silent", false));
        if (living instanceof org.bukkit.entity.Ageable ageable) {
            if (config.getBoolean("mob.baby", false)) ageable.setBaby(); else ageable.setAdult();
        }
        applyEquipment(living.getEquipment());
    }

    private void applyEquipment(EntityEquipment equipment) {
        if (equipment == null) return;
        var section = plugin.getConfig().getConfigurationSection("mob.equipment");
        if (section == null) return;
        setItem(section.getString("helmet"), equipment::setHelmet);
        setItem(section.getString("chestplate"), equipment::setChestplate);
        setItem(section.getString("leggings"), equipment::setLeggings);
        setItem(section.getString("boots"), equipment::setBoots);
        setItem(section.getString("main-hand"), equipment::setItemInMainHand);
        setItem(section.getString("off-hand"), equipment::setItemInOffHand);
    }

    private void setItem(String materialName, java.util.function.Consumer<ItemStack> setter) {
        if (materialName == null || materialName.isBlank()) return;
        Material material = Material.matchMaterial(materialName);
        if (material != null && material.isItem()) setter.accept(new ItemStack(material));
    }

    public MobRecord recognize(Entity entity) {
        String id = entity.getPersistentDataContainer().get(plugin.mobIdKey(), PersistentDataType.STRING);
        if (id == null) return null;
        String name = entity.getPersistentDataContainer().get(plugin.mobNameKey(), PersistentDataType.STRING);
        if (name == null || name.isBlank()) name = entity.getCustomName() == null ? "AI Mob" : entity.getCustomName();
        MobRecord record = new MobRecord(id, name, entity.getUniqueId(), entity);
        byId.put(id, record);
        return record;
    }

    public boolean isProtected(Entity entity) { return recognize(entity) != null; }

    public List<MobRecord> list() {
        List<MobRecord> records = new ArrayList<>(byId.values());
        records.sort(Comparator.comparing(MobRecord::name, String.CASE_INSENSITIVE_ORDER));
        return records;
    }

    public MobRecord find(String name) {
        String query = name.toLowerCase(Locale.ROOT);
        return byId.values().stream().filter(record -> record.name().equalsIgnoreCase(name)
                || record.id().equalsIgnoreCase(name)).findFirst().orElseGet(() -> byId.values().stream()
                .filter(record -> record.name().toLowerCase(Locale.ROOT).contains(query)).findFirst().orElse(null));
    }

    public boolean remove(String name) {
        MobRecord record = find(name);
        if (record == null) return false;
        return remove(record);
    }

    public boolean remove(MobRecord record) {
        if (record == null || byId.remove(record.id()) == null) return false;
        record.entity().getScheduler().run(plugin, task -> record.entity().remove(), null);
        plugin.chatService().endSessionsForMob(record.id());
        return true;
    }

    public ItemStack createRemovalStick() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("stick.material", "STICK"));
        if (material == null || !material.isItem()) material = Material.STICK;
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        String display = plugin.getConfig().getString("stick.name", "<red>AI Mob Removal Stick</red>");
        meta.displayName(MiniMessage.miniMessage().deserialize(display));
        List<String> lore = plugin.getConfig().getStringList("stick.lore");
        meta.lore(lore.stream().map(MiniMessage.miniMessage()::deserialize).toList());
        int modelData = plugin.getConfig().getInt("stick.custom-model-data", -1);
        if (modelData >= 0) meta.setCustomModelData(modelData);
        meta.getPersistentDataContainer().set(plugin.stickKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isRemovalStick(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(plugin.stickKey(), PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void forget(Entity entity) {
        String id = entity.getPersistentDataContainer().get(plugin.mobIdKey(), PersistentDataType.STRING);
        if (id != null) byId.remove(id);
    }
}
