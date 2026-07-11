package com.example.version_control_system.common;

import com.example.version_control_system.exception.BusinessException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模板 field_schema 的解析与校验（§3.3）。
 * <p>结构：{@code {"fields":[{"key","label","type","required?","options?","unit?","showOnCard?",...}]}}；
 * type ∈ TEXT/NUMBER/ENUM/DATE/IMAGE。</p>
 */
public final class FieldSchema {

    /** 允许的字段类型。FILE 已更名为 IMAGE（Phase 2）。 */
    public static final List<String> TYPES = List.of("TEXT", "NUMBER", "ENUM", "DATE", "IMAGE");

    public record Field(String key, String label, String type, boolean required, List<String> options,
                        boolean showOnCard, String unit, boolean keyMetric) {
    }

    private final List<Field> fields;

    private FieldSchema(List<Field> fields) {
        this.fields = fields;
    }

    public List<Field> fields() {
        return fields;
    }

    /**
     * 解析并校验 field_schema JSON。为 null/空视为无字段（合法）。
     * <p>校验：顶层须为对象且含 fields 数组；每个字段 key/label 非空、type 合法、ENUM 须有非空 options。
     * key 不可重复。非法抛 BAD_REQUEST。</p>
     */
    public static FieldSchema parse(JsonNode root) {
        List<Field> result = new ArrayList<>();
        if (root == null || root.isNull()) {
            return new FieldSchema(result);
        }
        if (!root.isObject()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "field_schema 顶层必须是对象");
        }
        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || fieldsNode.isNull()) {
            return new FieldSchema(result);
        }
        if (!fieldsNode.isArray()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "field_schema.fields 必须是数组");
        }
        List<String> seenKeys = new ArrayList<>();
        for (JsonNode f : fieldsNode) {
            String key = text(f, "key");
            String label = text(f, "label");
            String type = text(f, "type");
            // key 可由后端自动生成（前端不再显示 key 输入框）
            if (key == null || key.isBlank()) {
                key = "f_" + UUID.randomUUID().toString().substring(0, 8);
            }
            if (label == null || label.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "字段 label 不能为空（key=" + key + "）");
            }
            if (type == null || !TYPES.contains(type)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "字段 type 非法（key=" + key + "）：" + type);
            }
            if (seenKeys.contains(key)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "字段 key 重复：" + key);
            }
            seenKeys.add(key);

            boolean required = f.has("required") && f.get("required").asBoolean(false);
            List<String> options = new ArrayList<>();
            JsonNode optionsNode = f.get("options");
            if (optionsNode != null && optionsNode.isArray()) {
                for (JsonNode o : optionsNode) {
                    options.add(o.asString());
                }
            }
            if ("ENUM".equals(type) && options.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "ENUM 字段必须提供非空 options（key=" + key + "）");
            }
            boolean showOnCard = f.has("showOnCard") && f.get("showOnCard").asBoolean(false);
            boolean keyMetric = f.has("keyMetric") && f.get("keyMetric").asBoolean(false);
            String unit = text(f, "unit");
            result.add(new Field(key, label, type, required, options, showOnCard, unit, keyMetric));
        }
        return new FieldSchema(result);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }
}
