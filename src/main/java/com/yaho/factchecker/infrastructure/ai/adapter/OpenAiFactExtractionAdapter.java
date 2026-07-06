package com.yaho.factchecker.infrastructure.ai.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.application.ai.port.FactExtractionPort;
import com.yaho.factchecker.domain.ai.dto.command.AiCallLogFailureCommand;
import com.yaho.factchecker.domain.ai.dto.command.AiCallLogSuccessCommand;
import com.yaho.factchecker.domain.ai.dto.request.FactExtractionRequest;
import com.yaho.factchecker.domain.ai.dto.response.FactExtractionResponse;
import com.yaho.factchecker.domain.ai.service.AiCallLogService;
import com.yaho.factchecker.domain.ai.type.AiCallType;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.infrastructure.ai.prompt.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiFactExtractionAdapter implements FactExtractionPort {

    private static final String PROMPT_PATH = "prompts/ai/fact-extraction-system.txt";

    private final ChatClient chatClient;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final String model;
    private final AiCallLogService aiCallLogService;

    public OpenAiFactExtractionAdapter(
            ChatClient.Builder chatClientBuilder,
            PromptLoader promptLoader,
            ObjectMapper objectMapper,
            @Value("${ai.openai.model.fact-extraction}") String model,
            AiCallLogService aiCallLogService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.promptLoader = promptLoader;
        this.objectMapper = objectMapper;
        this.model = model;
        this.aiCallLogService = aiCallLogService;
    }

    @Override
    public FactExtractionResponse extract(FactExtractionRequest request) {
        String systemPrompt = promptLoader.load(PROMPT_PATH);
        String inputData = request.documentContent();
        long startTime = System.currentTimeMillis();

        String content;

        try {
            content = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .build())
                    .system(systemPrompt)
                    .user(inputData)
                    .call()
                    .content();
        } catch (Exception e) {
            saveFailure(inputData, null, ErrorCode.AI_API_CALL_FAILED, e.getMessage(), startTime);
            throw new BusinessException(ErrorCode.AI_API_CALL_FAILED, e);
        }

        if (content == null || content.isBlank()) {
            saveFailure(inputData, content, ErrorCode.AI_EMPTY_RESPONSE, "AI 응답이 비어있습니다.", startTime);
            throw new BusinessException(ErrorCode.AI_EMPTY_RESPONSE);
        }

        try {
            FactExtractionResponse response =
                    objectMapper.readValue(content, FactExtractionResponse.class);

            saveSuccess(inputData, content, startTime);

            return response;
        } catch (JsonProcessingException e) {
            saveFailure(inputData, content, ErrorCode.AI_RESPONSE_PARSE_FAILED, e.getMessage(), startTime);
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_FAILED, e);
        }
    }

    private void saveSuccess(String inputData, String outputData, long startTime) {
        aiCallLogService.saveSuccess(AiCallLogSuccessCommand.builder()
                .callType(AiCallType.FACT_EXTRACTION)
                .modelName(model)
                .promptPath(PROMPT_PATH)
                .promptVersion(null)
                .inputData(inputData)
                .outputData(outputData)
                .latencyMs(System.currentTimeMillis() - startTime)
                .build());
    }

    private void saveFailure(
            String inputData,
            String outputData,
            ErrorCode errorCode,
            String errorMessage,
            long startTime
    ) {
        aiCallLogService.saveFailure(AiCallLogFailureCommand.builder()
                .callType(AiCallType.FACT_EXTRACTION)
                .modelName(model)
                .promptPath(PROMPT_PATH)
                .promptVersion(null)
                .inputData(inputData)
                .outputData(outputData)
                .errorCode(errorCode.getCode())
                .errorMessage(errorMessage)
                .latencyMs(System.currentTimeMillis() - startTime)
                .build());
    }
}
