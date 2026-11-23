package dev.aimob.command;

import dev.aimob.AiMobPlugin;
import dev.aimob.service.ChatService;
import dev.aimob.service.MobManager;
import dev.aimob.util.Messages;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AiMobCommand implements CommandExecutor, TabCompleter {
    private final AiMobPlugin plugin;
    private final MobManager mobs;
    private final Messages messages;

    public AiMobCommand(AiMobPlugin plugin, MobManager mobs, ChatService chat, Messages messages) {
        this.plugin = plugin; this.mobs = mobs; this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sender.sendMessage(messages.prefixed("usage")); return true; }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("reload")) {
            if (!sender.hasPermission("aimob.admin")) { sender.sendMessage(messages.prefixed("no-permission")); return true; }
            plugin.reloadPluginConfig();
            sender.sendMessage(messages.prefixed("reloaded"));
            return true;
        }
        if (!sender.hasPermission("aimob.admin")) { sender.sendMessage(messages.prefixed("no-permission")); return true; }
        switch (subcommand) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(messages.prefixed("players-only")); return true; }
                String name = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
                MobManager.MobRecord mob = mobs.spawn(player, name);
                if (mob == null) sender.sendMessage(messages.prefixed("spawn-failed"));
                else sender.sendMessage(messages.prefixed("spawned", Map.of("mob_name", escape(mob.name()), "id", mob.id())));
                return true;
            }
            case "list" -> {
                List<MobManager.MobRecord> listed = mobs.list();
                if (listed.isEmpty()) sender.sendMessage(messages.prefixed("list-empty"));
                else listed.forEach(mob -> sender.sendMessage(messages.prefixed("list-entry",
                        Map.of("mob_name", escape(mob.name()), "id", mob.id()))));
                return true;
            }
            case "remove" -> {
                if (args.length < 2) { sender.sendMessage(messages.prefixed("usage")); return true; }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                MobManager.MobRecord mob = mobs.find(name);
                if (mob == null) sender.sendMessage(messages.prefixed("not-found", Map.of("mob_name", escape(name))));
                else if (mobs.remove(mob)) sender.sendMessage(messages.prefixed("removed", Map.of("mob_name", escape(mob.name()))));
                else sender.sendMessage(messages.prefixed("not-found", Map.of("mob_name", escape(name))));
                return true;
            }
            case "stick" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(messages.prefixed("players-only")); return true; }
                var leftovers = player.getInventory().addItem(mobs.createRemovalStick());
                leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                player.sendMessage(messages.prefixed("stick-given"));
                return true;
            }
            default -> sender.sendMessage(messages.prefixed("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "list", "remove", "stick", "reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return mobs.list().stream().map(MobManager.MobRecord::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }

    private String escape(String value) { return MiniMessage.miniMessage().escapeTags(value); }
}
