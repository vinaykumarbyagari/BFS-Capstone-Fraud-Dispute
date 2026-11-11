package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.ProcessorMode;
import com.capstone.fraud.policy.PolicyRetriever;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.service.CaseMemoryService;
import com.capstone.fraud.service.InvestigationTracer;
import com.capstone.fraud.tools.EvidenceToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Spring AI implementation of {@link FraudCaseProcessor}. The model bean
 * is built manually so the application runs in rules mode when no API
 * key is configured.
 */
@Slf4j
@Component
public class SpringAiFraudCaseProcessor extends AbstractAiFraudCaseProcessor {

    private final FraudProperties properties;
    private AnthropicChatModel chatModel;

    public SpringAiFraudCaseProcessor(EvidenceToolExecutor executor, PolicyRetriever retriever,
                                      InvestigationTracer tracer, RulesAnalyzer rulesAnalyzer,
                                      PromptFactory prompts, ContractValidator contractValidator,
                                      CaseMemoryService memory, RecommendationRepository recommendationRepo,
                                      ObjectMapper objectMapper, FraudProperties properties) {
        super(executor, retriever, tracer, rulesAnalyzer, prompts, contractValidator,
                memory, recommendationRepo, objectMapper, properties);
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String key = properties.getAnthropic().getApiKey();
        if (!StringUtils.hasText(key)) {
            log.info("Spring AI processor: no ANTHROPIC_API_KEY — unavailable, rules mode applies");
            return;
        }
        AnthropicApi.Builder apiBuilder = AnthropicApi.builder().apiKey(key);
        if (StringUtils.hasText(properties.getAnthropic().getBaseUrl())) {
            apiBuilder.baseUrl(properties.getAnthropic().getBaseUrl());
        }
        AnthropicApi api = apiBuilder.build();
        this.chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(properties.getAnthropic().getModel())
                        .maxTokens(2048)
                        .temperature(0.1)
                        .build())
                .build();
        log.info("Spring AI processor ready (model {})", properties.getAnthropic().getModel());
    }

    @Override
    public ProcessorMode mode() {
        return ProcessorMode.SPRING_AI;
    }

    @Override
    public boolean available() {
        return chatModel != null;
    }

    @Override
    protected String modelName() {
        return "spring-ai:" + properties.getAnthropic().getModel();
    }

    @Override
    protected String callModel(String systemPrompt, String userPrompt) {
        ChatResponse response = chatModel.call(new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
        String content = response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("empty model response");
        }
        return content;
    }
}
