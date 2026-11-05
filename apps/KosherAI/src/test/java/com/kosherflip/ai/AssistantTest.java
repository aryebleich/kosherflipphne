package com.kosherflip.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kosherflip.common.WordFilter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AssistantTest {
    private static final class Fake implements Provider {
        String answer;
        final List<Integer> sizes = new ArrayList<>();
        Fake(String a) { answer = a; }
        @Override public String ask(String system, List<String[]> history) {
            sizes.add(history.size());
            return answer;
        }
    }

    @Test public void cleanAnswerPassesAndHistoryGrows() {
        Fake f = new Fake("Paris is the capital of France.");
        Assistant a = new Assistant(f, WordFilter.standard(null));
        assertEquals("Paris is the capital of France.", a.ask("Capital of France?"));
        a.ask("And of Italy?");
        assertEquals(Integer.valueOf(3), f.sizes.get(1));
    }

    @Test public void badQuestionNeverSent() {
        Fake f = new Fake("x");
        Assistant a = new Assistant(f, WordFilter.standard(null));
        assertEquals(Assistant.FILTERED, a.ask("show me porn"));
        assertTrue(f.sizes.isEmpty());
    }

    @Test public void badAnswerReplaced() {
        Assistant a = new Assistant(new Fake("here are some nude pictures"), WordFilter.standard(null));
        assertEquals(Assistant.FILTERED, a.ask("hello"));
    }

    @Test public void geminiParsing() throws Exception {
        assertEquals("Hi there", GeminiProvider.parse(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi \"},{\"text\":\"there\"}]},\"finishReason\":\"STOP\"}]}"));
        assertEquals(Assistant.FILTERED, GeminiProvider.parse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        assertEquals(Assistant.FILTERED, GeminiProvider.parse("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}"));
    }
}
