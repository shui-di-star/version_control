package com.example.version_control_system.common;

import com.example.version_control_system.entity.Asset;
import com.example.version_control_system.entity.EntityTemplate;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.AssetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 依模板 field_schema 校验实体 attributes（§3.3 + 决策 10）。
 * <p>校验必填、类型、ENUM 选项；FILE 字段值须为归属当前实体的 asset id。</p>
 */
@Component
public class AttributeValidator {

    private final JsonUtils jsonUtils;
    private final AssetMapper assetMapper;

    public AttributeValidator(JsonUtils jsonUtils, AssetMapper assetMapper) {
        this.jsonUtils = jsonUtils;
        this.assetMapper = assetMapper;
    }

    /**
     * 校验 attributes 是否符合模板定义并返回归一化 JSON 串（null/空返回 null）。
     *
     * @param entityId 当前实体 id；为 null（创建阶段）时跳过 FILE 归属校验。
     */
    public String validate(EntityTemplate template, String attributes, Long entityId) {
        FieldSchema schema = FieldSchema.parse(jsonUtils.parse(template.getFieldSchema()));
        JsonNode attrs = jsonUtils.parse(attributes);
        if (attrs != null && !attrs.isObject()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "attributes 必须是 JSON 对象");
        }
        for (FieldSchema.Field field : schema.fields()) {
            JsonNode value = attrs == null ? null : attrs.get(field.key());
            boolean absent = value == null || value.isNull();
            if (absent) {
                if (field.required()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "缺少必填字段：" + field.key());
                }
                continue;
            }
            checkType(field, value, entityId);
        }
        return attributes == null || attributes.isBlank() ? null : attributes;
    }

    private void checkType(FieldSchema.Field field, JsonNode value, Long entityId) {
        String key = field.key();
        switch (field.type()) {
            case "TEXT" -> require(value.isString(), key, "应为文本");
            case "NUMBER" -> require(value.isNumber(), key, "应为数字");
            case "DATE" -> require(value.isString(), key, "应为日期字符串");
            case "ENUM" -> {
                require(value.isString(), key, "应为枚举字符串");
                if (!field.options().contains(value.asString())) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                            "字段 " + key + " 取值不在 options 内：" + value.asString());
                }
            }
            case "FILE" -> {
                require(value.isIntegralNumber(), key, "应为 asset id");
                if (entityId != null) {
                    checkFileOwnership(key, value.asLong(), entityId);
                }
            }
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "未知字段类型：" + field.type());
        }
    }

    private void checkFileOwnership(String key, Long assetId, Long entityId) {
        List<Asset> owned = assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getId, assetId)
                .eq(Asset::getEntityId, entityId));
        if (owned.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "字段 " + key + " 指向的产出物不存在或不属于当前实体：" + assetId);
        }
    }

    private void require(boolean ok, String key, String msg) {
        if (!ok) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "字段 " + key + " " + msg);
        }
    }
}
