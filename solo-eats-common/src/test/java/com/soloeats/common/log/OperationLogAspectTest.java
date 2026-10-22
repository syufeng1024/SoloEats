package com.soloeats.common.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogAspectTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("password/phone/idCard/bankAccount 落库前替换为 ***，且仍为合法 JSON")
    void masksSensitiveFields() throws Exception {
        String raw = "[{"
                + "\"username\":\"admin\","
                + "\"password\":\"Secret@123\","
                + "\"phone\":\"13800138000\","
                + "\"idCard\":\"110101199001011234\","
                + "\"bankAccount\":\"6222021234567890\","
                + "\"nickname\":\"运营\""
                + "}]";

        String masked = OperationLogAspect.sanitizeParams(raw);

        assertThat(masked)
                .doesNotContain("Secret@123")
                .doesNotContain("13800138000")
                .doesNotContain("110101199001011234")
                .doesNotContain("6222021234567890")
                .contains("\"password\":\"***\"")
                .contains("\"phone\":\"***\"")
                .contains("\"idCard\":\"***\"")
                .contains("\"bankAccount\":\"***\"")
                .contains("\"username\":\"admin\"");

        JsonNode parsed = objectMapper.readTree(masked);
        assertThat(parsed.get(0).get("nickname").asText()).isEqualTo("运营");
    }

    @Test
    @DisplayName("超长参数截断后仍是合法 JSON，并标记 truncated=true")
    void truncatesToValidJson() throws Exception {
        String raw = "[{\"remark\":\"" + "a".repeat(5000) + "\"}]";

        String masked = OperationLogAspect.sanitizeParams(raw);

        assertThat(masked.length()).isLessThanOrEqualTo(2000);
        JsonNode parsed = objectMapper.readTree(masked);
        assertThat(parsed.get("truncated").asBoolean()).isTrue();
        assertThat(parsed.get("preview").asText()).isNotBlank();
    }
}
