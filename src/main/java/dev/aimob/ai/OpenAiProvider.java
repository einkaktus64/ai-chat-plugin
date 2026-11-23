package dev.aimob.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aimob.AiMobPlugin;
import java.util.List;

public final class OpenAiProvider extends HttpAiProvider {
    public OpenAiProvider(AiMobPlugin plugin) { super(plugin); }
    @Override protected String endpoint() { return "/chat/completions"; }
    @Override protected boolean requiresKey() { return true; }

    @Override protected JsonObject payload(List<ChatMessage> messages) {
        JsonObject request = new JsonObject();
        request.addProperty("model", plugin.getConfig().getString("model", "gpt-4o-mini"));
        request.addProperty("temperature", plugin.getConfig().getDouble("temperature", 0.7));
        request.addProperty("max_tokens", plugin.getConfig().getInt("max-tokens", 250));
        JsonArray array = new JsonArray();
        for (ChatMessage message : messages) {
            JsonObject item = new JsonObject();
            item.addProperty("role", message.role());
            item.addProperty("content", message.content());
            array.add(item);
        }
        request.add("messages", array);
        return request;
    }

    @Override protected String extract(String response) {
        return parse(response).getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }
}
