package com.kosherflip.ai;

import java.util.List;

/** An AI model service. */
interface Provider {
    /** @param history {role ("user"|"assistant"), text} pairs, oldest first, ending with the user's question. */
    String ask(String system, List<String[]> history) throws Exception;

    /** An error whose message can be shown to the user as is. */
    final class FriendlyException extends Exception {
        FriendlyException(String message) {
            super(message);
        }
    }
}
