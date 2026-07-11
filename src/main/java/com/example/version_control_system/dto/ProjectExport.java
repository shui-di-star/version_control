package com.example.version_control_system.dto;

import java.util.List;

/**
 * 项目导出/导入载荷：项目全量模板/实体/关系/产出物元信息。
 * <p>导出时保留原始 id 供导入端建立旧→新映射；导入时一律分配新 id 并按映射重建引用。</p>
 */
public record ProjectExport(
        List<ExportEntityTemplate> entityTemplates,
        List<ExportRelationTemplate> relationTemplates,
        List<ExportEntity> entities,
        List<ExportRelation> relations,
        List<ExportAsset> assets) {

    public record ExportEntityTemplate(Long id, String name, String fieldSchema) {
    }

    public record ExportRelationTemplate(Long id, String name, Integer directed, String lineStyle,
                                         String allowedFrom, String allowedTo) {
    }

    public record ExportEntity(Long id, Long templateId, Long parentId, String name, String status,
                               Integer isMilestone, String remark, String attributes) {
    }

    public record ExportRelation(Long id, Long templateId, Long fromEntityId, Long toEntityId, String remark) {
    }

    public record ExportAsset(Long id, Long entityId, String assetType, String fileName, String objectKey,
                              String contentText, Long size, String mimeType) {
    }
}
