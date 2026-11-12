package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.ProcessorMode;
import com.capstone.fraud.policy.PolicyRetriever;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.service.CaseMemoryService;
import com.capstone.fraud.service.InvestigationTracer;
import com.capstone.fraud.tools.EvidenceToolExecutor;
import com.capstone.fraud.memory.CaseChatMemoryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j implementation of {@link FraudCaseProcessor} — same
 * contract and pipeline as the Spring AI mode, different model client.
 */
@Slf4j
@Component
public class LangChain4jFraudCaseProcessor extends AbstractAiFraudCaseProcessor {

    private final FraudProperties properties;
    private final CaseChatMemoryAdapter memoryAdapter;
    private AnthropicChatModel model;

    public LangChain4jFraudCaseProcessor(EvidenceToolExecutor executor, PolicyRetriever retriever,
                                         InvestigationTracer tracer, RulesAnalyzer rulesAnalyzer,
                                         PromptFactory prompts, ContractValidator contractValidator,
                                         CaseMemoryService memory, RecommendationRepository recommendationRepo,
                                         ObjectMapper objectMapper, FraudProperties properties,
                                         CaseChatMemoryAdapter memoryAdapter) {
        super(executor, retriever, tracer, rulesAnalyzer, prompts, contractValidator,
                memory, recommendationRepo, objectMapper, properties);
        this.properties = properties;
        this.memoryAdapter = memoryAdapter;
    }

    @PostConstruct
    void init() {
        String key = properties.getAnthropic().getApiKey();
        if (!StringUtils.hasText(key)) {
            log.info("LangChain4j processor: no ANTHROPIC_API_KEY — unavailable, rules mode applies");
            return;
        }
        var modelBuilder = AnthropicChatModel.builder()
                .apiKey(key)
                .modelName(properties.getAnthropic().getModel())
                .maxTokens(2048)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(90));
        String baseUrl = properties.getAnthropic().getBaseUrl();
        if (StringUtils.hasText(baseUrl)) {
            String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            modelBuilder.baseUrl(root + "/v1/");
        }
        this.model = modelBuilder.build();
        log.info("LangChain4j processor ready (model {})", properties.getAnthropic().getModel());
    }

    @Override
    public ProcessorMode mode() {
        return ProcessorMode.LANGCHAIN4J;
    }

    @Override
    public boolean available() {
        return model != null;
    }

    @Override
    protected String modelName() {
        return "langchain4j:" + properties.getAnthropic().getModel();
    }

    @Override
    protected String callModel(String systemPrompt, String userPrompt, String caseId) {
        // framework chat memory: JPA-backed, memory id = case id
        List<ChatMessage> history = dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .id(caseId)
                .maxMessages(10)
                .chatMemoryStore(memoryAdapter)
                .build()
                .messages();
        List<ChatMessage> messages = new ArrayList<>(history.size() + 2);
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(history);
        messages.add(UserMessage.from(userPrompt));
        ChatResponse response = model.chat(ChatRequest.builder().messages(messages).build());
        String text = response.aiMessage() == null ? null : response.aiMessage().text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("empty model response");
        }
        return text;
    }
}
