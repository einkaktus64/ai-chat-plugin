package dev.aimob.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aimob.AiMobPlugin;
import java.util.List;

public final class OllamaProvider extends HttpAiProvider {
    public OllamaProvider(AiMobPlugin plugin) { super(plugin); }
    @Override protected String endpoint() { return "/api/chat"; }

    @Override protected JsonObject payload(List<ChatMessage> messages) {
        JsonObject request = new JsonObject();
        request.addProperty("model", plugin.getConfig().getString("model", "llama3.2"));
        request.addProperty("stream", false);
        JsonObject options = new JsonObject();
        options.addProperty("temperature", plugin.getConfig().getDouble("temperature", 0.7));
        options.addProperty("num_predict", plugin.getConfig().getInt("max-tokens", 250));
        request.add("options", options);
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
        return parse(response).getAsJsonObject("message").get("content").getAsString();
    }
}
