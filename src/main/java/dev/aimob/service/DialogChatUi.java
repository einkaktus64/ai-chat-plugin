package dev.aimob.service;

import dev.aimob.AiMobPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/** Paper 26.3 text-input dialog front end; chat remains the compatibility fallback. */
public final class DialogChatUi {
    private final AiMobPlugin plugin;
    private final ChatService chat;

    public DialogChatUi(AiMobPlugin plugin, ChatService chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    public boolean show(Player player, String mobName) {
        try {
            int maxLength = Math.max(1, plugin.getConfig().getInt("max-message-length", 500));
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(Component.text("Chat with " + mobName))
                            .inputs(List.of(DialogInput.text("message", Component.text("Message"))
                                    .maxLength(maxLength).width(300).build()))
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.create(Component.text("Send"), null, 150,
                                    DialogAction.customClick((view, audience) -> {
                                        if (!(audience instanceof Player target)) return;
                                        String text = view.getText("message");
                                        target.getScheduler().run(plugin, task -> {
                                            chat.onChat(target, text == null ? "" : text);
                                            if (chat.isActive(target.getUniqueId())) show(target, mobName);
                                        }, null);
                                    }, ClickCallback.Options.builder().uses(100).build())),
                            ActionButton.create(Component.text("Close"), null, 100, null))));
            player.showDialog(dialog);
            return true;
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().fine("Paper text-input dialogs are unavailable; using chat mode.");
            return false;
        }
    }
}
