package com.example.version_control_system.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 全局 Jackson 定制：把 {@code Long} 包装类型序列化为字符串。
 * <p>原因：全表主键为雪花 id（19 位 {@code BIGINT}），超过 JavaScript
 * {@code Number.MAX_SAFE_INTEGER}（2^53-1），若以 JSON 数字返回，前端解析会丢精度导致 id 对不上。
 * 输出为字符串后前端零负担。</p>
 * <p>仅命中包装类型 {@code Long}——统计接口 {@link com.example.version_control_system.dto.ProjectStatsVO}
 * 的计数用基本型 {@code long}、里程碑/状态用 {@code Integer}，均不受影响，仍以数字返回。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            // 反序列化器：接受 JSON 字符串 "123" 和数字 123 两种形式，保证导出→导入 round-trip。
            // 导出时 Long→String，前端原样发回后端，若不注册此反序列化器会因 String→Long 失败而 400/500。
            module.addDeserializer(Long.class, new StringAcceptingLongDeserializer());
            builder.addModule(module);
        };
    }

    /**
     * 兼容 JSON 字符串和数字两种输入的 Long 反序列化器。
     * <p>场景：全局序列化把 Long 输出为字符串，导入接口收到前端原样回传的字符串 id，
     * 标准反序列化器不接受字符串→Long 的隐式转换，此处显式处理。</p>
     */
    static class StringAcceptingLongDeserializer extends StdDeserializer<Long> {
        StringAcceptingLongDeserializer() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) {
            return switch (p.currentToken()) {
                case VALUE_NUMBER_INT -> p.getLongValue();
                case VALUE_STRING -> {
                    String text = p.getText();
                    if (text == null || text.isBlank()) {
                        yield null;
                    }
                    yield Long.valueOf(text.trim());
                }
                case VALUE_NULL -> null;
                default -> ctxt.reportInputMismatch(Long.class,
                        "Expected NUMBER or STRING for Long, got %s", p.currentToken());
            };
        }
    }
}
