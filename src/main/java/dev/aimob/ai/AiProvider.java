package dev.aimob.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AiProvider {
    record ChatMessage(String role, String content) { }
    CompletableFuture<String> complete(List<ChatMessage> messages);
}
