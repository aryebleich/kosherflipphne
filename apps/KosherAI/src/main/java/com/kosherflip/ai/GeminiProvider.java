package com.kosherflip.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/**
 * Google Gemini through its REST API. Google AI Studio gives a FREE API key
 * (aistudio.google.com -> Get API key) with a generous free daily quota.
 * Google's own safety filters are set to their strictest level on top of our prompt.
 */
final class GeminiProvider implements Provider {
    static final String DEFAULT_MODEL = "gemini-flash-latest";
    private static final String[] HARMS = {
            "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_DANGEROUS_CONTENT"};

    private final String key;
    private final String model;

    GeminiProvider(String key, String model) {
        this.key = key;
        this.model = model == null || model.isEmpty() ? DEFAULT_MODEL : model;
    }

    @Override
    public String ask(String system, List<String[]> history) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", system))));
        JSONArray contents = new JSONArray();
        for (String[] m : history) {
            contents.put(new JSONObject()
                    .put("role", "user".equals(m[0]) ? "user" : "model")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", m[1]))));
        }
        body.put("contents", contents);
        JSONArray safety = new JSONArray();
        for (String h : HARMS) safety.put(new JSONObject().put("category", h).put("threshold", "BLOCK_LOW_AND_ABOVE"));
        body.put("safetySettings", safety);
        body.put("generationConfig", new JSONObject().put("maxOutputTokens", 2048));

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        Http.Response r = Http.postJson(url, Collections.singletonMap("x-goog-api-key", key), body);
        if (r.status == 400 && r.body.contains("API_KEY")) {
            throw new FriendlyException("The AI key is not valid. Please ask the phone's admin to check it in KosherGuard.");
        }
        Http.check(r);
        return parse(r.body);
    }

    static String parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONObject fb = o.optJSONObject("promptFeedback");
        if (fb != null && fb.has("blockReason")) return Assistant.FILTERED;
        JSONArray cands = o.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) return Assistant.FILTERED;
        JSONObject c = cands.getJSONObject(0);
        if ("SAFETY".equals(c.optString("finishReason"))) return Assistant.FILTERED;
        JSONObject content = c.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) return Assistant.FILTERED;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.getJSONObject(i);
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"));
        }
        return sb.toString();
    }
}
