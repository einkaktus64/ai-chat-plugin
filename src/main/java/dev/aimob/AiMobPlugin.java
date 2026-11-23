package dev.aimob;

import dev.aimob.command.AiMobCommand;
import dev.aimob.listener.MobListener;
import dev.aimob.service.ChatService;
import dev.aimob.service.MobManager;
import dev.aimob.util.Messages;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class AiMobPlugin extends JavaPlugin {
    private NamespacedKey mobIdKey;
    private NamespacedKey mobNameKey;
    private NamespacedKey stickKey;
    private MobManager mobManager;
    private ChatService chatService;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mobIdKey = new NamespacedKey(this, "mob_id");
        mobNameKey = new NamespacedKey(this, "mob_name");
        stickKey = new NamespacedKey(this, "removal_stick");
        messages = new Messages(this);
        mobManager = new MobManager(this);
        chatService = new ChatService(this, mobManager, messages);
        getServer().getPluginManager().registerEvents(new MobListener(this, mobManager, chatService, messages), this);

        AiMobCommand command = new AiMobCommand(this, mobManager, chatService, messages);
        if (getCommand("aimob") != null) {
            getCommand("aimob").setExecutor(command);
            getCommand("aimob").setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        if (chatService != null) chatService.shutdown();
    }

    public NamespacedKey mobIdKey() { return mobIdKey; }
    public NamespacedKey mobNameKey() { return mobNameKey; }
    public NamespacedKey stickKey() { return stickKey; }
    public MobManager mobManager() { return mobManager; }
    public ChatService chatService() { return chatService; }
    public Messages messages() { return messages; }

    public void reloadPluginConfig() {
        reloadConfig();
        messages.reload();
        if (chatService != null) chatService.refreshSettings();
    }
}
