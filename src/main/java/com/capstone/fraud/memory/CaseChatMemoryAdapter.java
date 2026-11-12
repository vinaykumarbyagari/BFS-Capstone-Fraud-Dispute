package com.capstone.fraud.memory;

import com.capstone.fraud.domain.CaseMessage;
import com.capstone.fraud.repo.CaseMessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA-backed adapter exposing the persisted per-case message history
 * through both frameworks' native memory contracts:
 * <ul>
 *   <li>Spring AI {@link ChatMemoryRepository} — used with
 *       {@code MessageWindowChatMemory}, conversation id = case id.</li>
 *   <li>LangChain4j {@link ChatMemoryStore} — used with
 *       {@code MessageWindowChatMemory}, memory id = case id.</li>
 * </ul>
 * Isolation is inherent: every framework memory instance only ever
 * sees messages for its own case/memory id, so two cases can never
 * share context.
 */
@Component
public class CaseChatMemoryAdapter implements ChatMemoryRepository, ChatMemoryStore {

    /** Messages replayed to the model per case — matches the window used by both processors. */
    static final int MEMORY_WINDOW = 10;

    private final CaseMessageRepository messages;

    public CaseChatMemoryAdapter(CaseMessageRepository messages) {
        this.messages = messages;
    }

    private static <T> List<T> window(List<T> all) {
        return all.size() <= MEMORY_WINDOW ? all : all.subList(all.size() - MEMORY_WINDOW, all.size());
    }

    // ---- Spring AI ChatMemoryRepository ----

    @Override
    public List<String> findConversationIds() {
        return messages.findDistinctCaseIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return window(messages.findByCaseIdOrderBySeq(conversationId).stream()
                .map(this::toSpringMessage)
                .toList());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAll(String conversationId, List<Message> newMessages) {
        int seq = (int) messages.countByCaseId(conversationId);
        for (Message m : newMessages) {
            CaseMessage cm = new CaseMessage();
            cm.setCaseId(conversationId);
            cm.setRole(m.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT
                    ? CaseMessage.ROLE_ASSISTANT : CaseMessage.ROLE_USER);
            cm.setContent(m.getText() == null ? ""
                    : m.getText().length() > 7900 ? m.getText().substring(0, 7900) : m.getText());
            cm.setSeq(++seq);
            messages.save(cm);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByConversationId(String conversationId) {
        messages.deleteByCaseId(conversationId);
    }

    // ---- LangChain4j ChatMemoryStore ----

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return window(messages.findByCaseIdOrderBySeq(String.valueOf(memoryId)).stream()
                .map(this::toLc4jMessage)
                .map(m -> (ChatMessage) m)
                .toList());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMessages(Object memoryId, List<ChatMessage> newMessages) {
        String caseId = String.valueOf(memoryId);
        int existing = (int) messages.countByCaseId(caseId);
        int seq = existing;
        for (int i = existing; i < newMessages.size(); i++) {
            ChatMessage m = newMessages.get(i);
            CaseMessage cm = new CaseMessage();
            cm.setCaseId(caseId);
            cm.setRole(m instanceof AiMessage ? CaseMessage.ROLE_ASSISTANT : CaseMessage.ROLE_USER);
            String text = switch (m) {
                case UserMessage u -> u.singleText();
                case AiMessage a -> a.text();
                default -> m.toString();
            };
            cm.setContent(text == null ? "" : text.length() > 7900 ? text.substring(0, 7900) : text);
            cm.setSeq(++seq);
            messages.save(cm);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteMessages(Object memoryId) {
        messages.deleteByCaseId(String.valueOf(memoryId));
    }

    private Message toSpringMessage(CaseMessage m) {
        return CaseMessage.ROLE_ASSISTANT.equals(m.getRole())
                ? new AssistantMessage(m.getContent())
                : new org.springframework.ai.chat.messages.UserMessage(m.getContent());
    }

    private ChatMessage toLc4jMessage(CaseMessage m) {
        return CaseMessage.ROLE_ASSISTANT.equals(m.getRole())
                ? AiMessage.from(m.getContent())
                : UserMessage.from(m.getContent());
    }
}
