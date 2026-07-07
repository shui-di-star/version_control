package com.example.version_control_system.dto;

/**
 * 搜索命中项。{@code sourceType} = ENTITY / RELATION；命中实体时 entityId 有值、relationId 为 null，
 * 命中关系时反之。{@code snippet} 为匹配到的字段片段（name 或 remark）。
 */
public record SearchHit(String sourceType, Long entityId, Long relationId,
                        Long fromEntityId, Long toEntityId,
                        String field, String snippet) {
}
