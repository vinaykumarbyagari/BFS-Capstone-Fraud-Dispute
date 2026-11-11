package com.capstone.fraud.ai;

import java.util.Optional;

/** Extracts the first balanced {...} JSON object from model text output. */
public final class JsonExtractor {

    private JsonExtractor() {}

    public static Optional<String> extractObject(String text) {
        if (text == null) return Optional.empty();
        String t = text.strip();
        // strip markdown fences if present
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        int start = t.indexOf('{');
        if (start < 0) return Optional.empty();
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) {
                return Optional.of(t.substring(start, i + 1));
            }
        }
        return Optional.empty();
    }
}
