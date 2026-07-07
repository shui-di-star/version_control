package com.example.version_control_system.common;

import com.example.version_control_system.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 基于 Jackson 3（SB4 默认）的 JSON 解析/校验小工具，统一把解析异常转成业务错误。 */
@Component
public class JsonUtils {

    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析为 JsonNode；null/空串返回 null；非法 JSON 抛 BAD_REQUEST。 */
    public JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "JSON 格式非法：" + e.getOriginalMessage());
        }
    }

    /** 序列化对象为 JSON 字符串。 */
    public String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
