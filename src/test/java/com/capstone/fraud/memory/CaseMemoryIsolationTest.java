package com.capstone.fraud.memory;

import com.capstone.fraud.domain.CaseMessage;
import com.capstone.fraud.service.CaseMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conversation memory must be isolated per case id across both
 * framework memory contracts (Spring AI ChatMemoryRepository and
 * LangChain4j ChatMemoryStore), and bounded by the message window.
 */
@SpringBootTest
class CaseMemoryIsolationTest {

    @Autowired
    CaseMemoryService memory;
    @Autowired
    CaseChatMemoryAdapter adapter;

    @Test
    void springAiMemoryIsIsolatedPerCaseId() {
        memory.append("case-iso-a", CaseMessage.ROLE_USER, "alpha run requested");
        memory.append("case-iso-a", CaseMessage.ROLE_ASSISTANT, "alpha recommendation");
        memory.append("case-iso-b", CaseMessage.ROLE_USER, "beta run requested");

        List<Message> a = adapter.findByConversationId("case-iso-a");
        List<Message> b = adapter.findByConversationId("case-iso-b");

        assertEquals(2, a.size());
        assertEquals(1, b.size());
        assertTrue(a.stream().noneMatch(m -> m.getText().contains("beta")));
        assertTrue(b.stream().noneMatch(m -> m.getText().contains("alpha")));
    }

    @Test
    void langChain4jStoreIsIsolatedPerMemoryId() {
        memory.append("case-iso-c", CaseMessage.ROLE_USER, "gamma request");
        memory.append("case-iso-d", CaseMessage.ROLE_USER, "delta request");

        var c = adapter.getMessages("case-iso-c");
        var d = adapter.getMessages("case-iso-d");
        assertEquals(1, c.size());
        assertEquals(1, d.size());
        assertTrue(c.get(0).toString().contains("gamma"));
        assertTrue(d.get(0).toString().contains("delta"));
    }

    @Test
    void messageWindowIsBounded() {
        for (int i = 0; i < 15; i++) {
            memory.append("case-iso-window", CaseMessage.ROLE_USER, "turn " + i);
        }
        MessageWindowChatMemory window = MessageWindowChatMemory.builder()
                .chatMemoryRepository(adapter)
                .maxMessages(10)
                .build();
        List<Message> got = window.get("case-iso-window");
        assertEquals(10, got.size());
        assertTrue(got.get(got.size() - 1).getText().contains("turn 14"));
    }
}
