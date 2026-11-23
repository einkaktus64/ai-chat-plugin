package dev.aimob.util;

import dev.aimob.AiMobPlugin;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Messages {
    private final AiMobPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(AiMobPlugin plugin) { this.plugin = plugin; }
    public void reload() { }

    public Component component(String path, Map<String, String> replacements) {
        String value = plugin.getConfig().getString("messages." + path, "");
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            value = value.replace("<" + replacement.getKey() + ">", replacement.getValue());
        }
        return miniMessage.deserialize(value);
    }

    public Component component(String path) { return component(path, Map.of()); }

    public Component prefixed(String path, Map<String, String> replacements) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        Component result = miniMessage.deserialize(prefix);
        return result.append(component(path, replacements));
    }

    public Component prefixed(String path) { return prefixed(path, Map.of()); }

    public String plain(String text) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(miniMessage.deserialize(text));
    }
}
