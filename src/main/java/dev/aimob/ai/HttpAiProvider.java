package dev.aimob.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aimob.AiMobPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class HttpAiProvider implements AiProvider {
    protected final AiMobPlugin plugin;
    protected final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    HttpAiProvider(AiMobPlugin plugin) { this.plugin = plugin; }
    protected abstract String endpoint();
    protected abstract JsonObject payload(List<ChatMessage> messages);
    protected abstract String extract(String response);
    protected boolean requiresKey() { return false; }

    @Override
    public CompletableFuture<String> complete(List<ChatMessage> messages) {
        String base = plugin.getConfig().getString("base-url", "").replaceAll("/+$", "");
        String key = plugin.getConfig().getString("api-key", "");
        if (key.isBlank()) key = System.getenv().getOrDefault("AIMOB_API_KEY", "");
        if (requiresKey() && key.isBlank()) return CompletableFuture.failedFuture(new IllegalStateException("API key is not configured"));
        int timeout = Math.max(1, plugin.getConfig().getInt("request-timeout-seconds", 30));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + endpoint()))
                .timeout(Duration.ofSeconds(timeout)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(messages).toString()));
        if (requiresKey()) builder.header("Authorization", "Bearer " + key);
        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("AI endpoint returned HTTP " + response.statusCode());
                    }
                    try {
                        String answer = extract(response.body());
                        if (answer == null || answer.isBlank()) throw new IllegalStateException("AI response contained no message");
                        return answer;
                    } catch (RuntimeException exception) {
                        throw new IllegalStateException("AI endpoint returned malformed JSON", exception);
                    }
                });
    }

    protected JsonObject parse(String response) {
        return JsonParser.parseString(response).getAsJsonObject();
    }
}
