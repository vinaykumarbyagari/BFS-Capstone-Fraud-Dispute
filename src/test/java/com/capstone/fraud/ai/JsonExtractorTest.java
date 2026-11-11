package com.capstone.fraud.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonExtractorTest {

    @Test
    void extractsPlainObject() {
        assertEquals("{\"a\":1}", JsonExtractor.extractObject("{\"a\":1}").orElseThrow());
    }

    @Test
    void stripsCodeFence() {
        String raw = "```json\n{\"a\":1}\n```";
        assertEquals("{\"a\":1}", JsonExtractor.extractObject(raw).orElseThrow());
    }

    @Test
    void findsObjectInsideProse() {
        String raw = "Here is my answer: {\"a\":{\"b\":\"x{y}\"}} hope it helps";
        assertEquals("{\"a\":{\"b\":\"x{y}\"}}", JsonExtractor.extractObject(raw).orElseThrow());
    }

    @Test
    void emptyWhenNoObject() {
        assertTrue(JsonExtractor.extractObject("no json here").isEmpty());
    }
}
