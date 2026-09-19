package com.byeori.global.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Chat Completions 클라이언트. 응답을 JSON 스키마로 고정(structured outputs)해 받는다.
 * 인증: Authorization: Bearer {API 키}. 서버에서만 호출(키 노출 금지).
 */
@Component
@Slf4j
public class OpenAiClient {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final ObjectMapper om = new ObjectMapper();

    /** 사용자가 화면에서 기다리는 호출이라 길게 끌지 않는다. */
    private final RestClient http;

    public OpenAiClient(@Value("${byeori.ai.openai-api-key:}") String apiKey,
                        @Value("${byeori.ai.openai-model:gpt-4o-mini}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 스키마에 맞는 JSON 한 덩어리를 받는다. 실패(키 없음·네트워크·거절·형식 오류) 시 null.
     *
     * @param schemaName 스키마 이름(영문·숫자·밑줄)
     * @param schema     JSON Schema. strict 모드라 모든 객체에 additionalProperties=false, 전 필드 required.
     */
    public JsonNode completeJson(String system, String user, String schemaName, Map<String, Object> schema) {
        if (!enabled()) return null;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.8,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)),
                    "response_format", Map.of(
                            "type", "json_schema",
                            "json_schema", Map.of("name", schemaName, "strict", true, "schema", schema)));

            String json = http.post().uri(URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = om.readTree(json);
            JsonNode msg = root.path("choices").path(0).path("message");
            // 안전 정책으로 거절하면 content 대신 refusal 이 온다
            if (msg.hasNonNull("refusal")) {
                log.warn("OpenAI 응답 거절: {}", msg.get("refusal").asText());
                return null;
            }
            JsonNode usage = root.path("usage");
            log.info("OpenAI 호출 model={} 입력토큰={} 출력토큰={}",
                    model, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt());
            return om.readTree(msg.path("content").asText());
        } catch (Exception e) {
            log.warn("OpenAI 호출 실패: {}", e.getMessage());
            return null;
        }
    }
}
