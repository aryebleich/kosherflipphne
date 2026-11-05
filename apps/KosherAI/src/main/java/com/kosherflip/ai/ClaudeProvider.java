package com.kosherflip.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

import java.util.List;

/** Claude (paid API key from console.anthropic.com), via the official Anthropic Java SDK. */
final class ClaudeProvider implements Provider {
    private final AnthropicClient client;
    private final String model;
    private boolean useFallbacks = true;

    ClaudeProvider(String apiKey, String model) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
    }

    @Override
    public String ask(String system, List<String[]> history) throws Exception {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        if (useFallbacks) {
            // Server-side refusal fallback: if a safety classifier declines, the API
            // retries on a suitable model instead of returning nothing.
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        for (String[] m : history) {
            if ("user".equals(m[0])) b.addUserMessage(m[1]);
            else b.addAssistantMessage(m[1]);
        }
        try {
            Message resp = client.messages().create(b.build());
            if (resp.stopReason().isPresent() && StopReason.REFUSAL.equals(resp.stopReason().get())) {
                return Assistant.FILTERED;
            }
            StringBuilder sb = new StringBuilder();
            resp.content().forEach(block -> block.text().ifPresent(t -> sb.append(t.text())));
            return sb.toString();
        } catch (BadRequestException e) {
            if (useFallbacks && String.valueOf(e.getMessage()).contains("fallback")) {
                useFallbacks = false; // account/region without the beta: retry plainly
                return ask(system, history);
            }
            throw new FriendlyException("The assistant could not answer that (" + e.statusCode() + ").");
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new FriendlyException("The AI key is not valid. Please ask the phone's admin to check it in KosherGuard.");
        } catch (RateLimitException e) {
            throw new FriendlyException("The assistant is busy right now. Please try again in a minute.");
        } catch (AnthropicServiceException e) {
            throw new FriendlyException("The assistant had a problem (" + e.statusCode() + "). Please try again.");
        } catch (AnthropicIoException e) {
            throw new FriendlyException("No internet connection. Please check your signal and try again.");
        }
    }
}
