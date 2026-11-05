package com.kosherflip.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Any service with an OpenAI-compatible "chat completions" API, e.g. the free
 * models on Groq (https://api.groq.com/openai/v1) or OpenRouter
 * (https://openrouter.ai/api/v1, models ending in ":free"), or a local server.
 */
final class OpenAiProvider implements Provider {
    private final String key, model, baseUrl;

    OpenAiProvider(String key, String model, String baseUrl) {
        this.key = key;
        this.model = model;
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.baseUrl = b;
    }

    @Override
    public String ask(String system, List<String[]> history) throws Exception {
        if (baseUrl.isEmpty() || model == null || model.isEmpty()) {
            throw new FriendlyException("The AI service is not fully set up. Please ask the phone's admin.");
        }
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", system));
        for (String[] m : history) messages.put(new JSONObject().put("role", m[0]).put("content", m[1]));
        JSONObject body = new JSONObject().put("model", model).put("messages", messages).put("max_tokens", 2048);
        Map<String, String> headers = new HashMap<>();
        if (key != null && !key.isEmpty()) headers.put("Authorization", "Bearer " + key);
        Http.Response r = Http.postJson(baseUrl + "/chat/completions", headers, body);
        Http.check(r);
        JSONObject o = new JSONObject(r.body);
        JSONArray choices = o.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return Assistant.FILTERED;
        JSONObject c = choices.getJSONObject(0);
        if ("content_filter".equals(c.optString("finish_reason"))) return Assistant.FILTERED;
        JSONObject msg = c.optJSONObject("message");
        return msg == null ? "" : msg.optString("content");
    }
}
