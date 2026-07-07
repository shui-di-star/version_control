package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.version_control_system.dto.ProjectExport;
import com.example.version_control_system.entity.Asset;
import com.example.version_control_system.entity.EntityTemplate;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.RelationTemplate;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.mapper.AssetMapper;
import com.example.version_control_system.mapper.EntityTemplateMapper;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.RelationTemplateMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.ProjectPortService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目导出/导入实现。
 * <p>导入的核心是"旧 id→新 id"重映射：先按依赖顺序（模板→实体→关系→产出物）插入并记录映射，
 * 再用映射改写引用列（parent_id/template_id/from/to/entity_id）。实体的 parent_id 可能指向
 * 同批次尚未插入的节点，因此先全部插入拿到新 id、再统一回填 parent_id。</p>
 */
@Service
public class ProjectPortServiceImpl implements ProjectPortService {

    private final EntityTemplateMapper entityTemplateMapper;
    private final RelationTemplateMapper relationTemplateMapper;
    private final SimEntityMapper entityMapper;
    private final RelationMapper relationMapper;
    private final AssetMapper assetMapper;

    public ProjectPortServiceImpl(EntityTemplateMapper entityTemplateMapper,
                                  RelationTemplateMapper relationTemplateMapper,
                                  SimEntityMapper entityMapper,
                                  RelationMapper relationMapper,
                                  AssetMapper assetMapper) {
        this.entityTemplateMapper = entityTemplateMapper;
        this.relationTemplateMapper = relationTemplateMapper;
        this.entityMapper = entityMapper;
        this.relationMapper = relationMapper;
        this.assetMapper = assetMapper;
    }

    @Override
    public ProjectExport export(Long projectId) {
        List<ProjectExport.ExportEntityTemplate> ets = entityTemplateMapper.selectList(
                        new LambdaQueryWrapper<EntityTemplate>().eq(EntityTemplate::getProjectId, projectId))
                .stream().map(t -> new ProjectExport.ExportEntityTemplate(
                        t.getId(), t.getName(), t.getIcon(), t.getFieldSchema())).toList();

        List<ProjectExport.ExportRelationTemplate> rts = relationTemplateMapper.selectList(
                        new LambdaQueryWrapper<RelationTemplate>().eq(RelationTemplate::getProjectId, projectId))
                .stream().map(t -> new ProjectExport.ExportRelationTemplate(
                        t.getId(), t.getName(), t.getDirected(), t.getLineStyle(),
                        t.getAllowedFrom(), t.getAllowedTo())).toList();

        List<SimEntity> entityRows = entityMapper.selectList(
                new LambdaQueryWrapper<SimEntity>().eq(SimEntity::getProjectId, projectId));
        List<ProjectExport.ExportEntity> entities = entityRows.stream().map(e ->
                new ProjectExport.ExportEntity(e.getId(), e.getTemplateId(), e.getParentId(), e.getName(),
                        e.getStatus(), e.getIsMilestone(), e.getRemark(), e.getAttributes())).toList();

        List<Relation> relationRows = relationMapper.selectList(
                new LambdaQueryWrapper<Relation>().eq(Relation::getProjectId, projectId));
        List<ProjectExport.ExportRelation> relations = relationRows.stream().map(r ->
                new ProjectExport.ExportRelation(r.getId(), r.getTemplateId(),
                        r.getFromEntityId(), r.getToEntityId(), r.getRemark())).toList();

        List<Long> entityIds = entityRows.stream().map(SimEntity::getId).toList();
        List<ProjectExport.ExportAsset> assets = entityIds.isEmpty() ? List.of()
                : assetMapper.selectList(new LambdaQueryWrapper<Asset>().in(Asset::getEntityId, entityIds))
                .stream().map(a -> new ProjectExport.ExportAsset(a.getId(), a.getEntityId(), a.getAssetType(),
                        a.getFileName(), a.getObjectKey(), a.getContentText(), a.getSize(), a.getMimeType())).toList();

        return new ProjectExport(ets, rts, entities, relations, assets);
    }

    @Override
    @Transactional
    public void importInto(Long projectId, ProjectExport data) {
        Map<Long, Long> entityTemplateIdMap = new HashMap<>();
        Map<Long, Long> relationTemplateIdMap = new HashMap<>();
        Map<Long, Long> entityIdMap = new HashMap<>();

        // 1) 实体模板
        for (ProjectExport.ExportEntityTemplate t : nullSafe(data.entityTemplates())) {
            EntityTemplate row = new EntityTemplate();
            row.setProjectId(projectId);
            row.setName(t.name());
            row.setIcon(t.icon());
            row.setFieldSchema(t.fieldSchema());
            entityTemplateMapper.insert(row);
            entityTemplateIdMap.put(t.id(), row.getId());
        }

        // 2) 关系模板（allowedFrom/allowedTo 引用实体模板 id，需按映射改写）
        for (ProjectExport.ExportRelationTemplate t : nullSafe(data.relationTemplates())) {
            RelationTemplate row = new RelationTemplate();
            row.setProjectId(projectId);
            row.setName(t.name());
            row.setDirected(t.directed());
            row.setLineStyle(t.lineStyle());
            row.setAllowedFrom(remapIdArray(t.allowedFrom(), entityTemplateIdMap));
            row.setAllowedTo(remapIdArray(t.allowedTo(), entityTemplateIdMap));
            relationTemplateMapper.insert(row);
            relationTemplateIdMap.put(t.id(), row.getId());
        }

        // 3) 实体：先插入拿新 id（parent_id 暂置 null），记录映射
        List<ProjectExport.ExportEntity> entities = nullSafe(data.entities());
        for (ProjectExport.ExportEntity e : entities) {
            SimEntity row = new SimEntity();
            row.setProjectId(projectId);
            row.setTemplateId(entityTemplateIdMap.get(e.templateId()));
            row.setParentId(null);
            row.setName(e.name());
            row.setStatus(e.status());
            row.setIsMilestone(e.isMilestone());
            row.setRemark(e.remark());
            row.setAttributes(e.attributes());
            entityMapper.insert(row);
            entityIdMap.put(e.id(), row.getId());
        }
        // 3b) 回填 parent_id（按旧→新映射）
        // 注意：不能用 new SimEntity() + updateById —— SimEntity.status 标注了
        // @TableField(updateStrategy = ALWAYS)，空对象的 null 值会覆盖第 3 步设好的 status。
        // 改用 LambdaUpdateWrapper 精准只更新 parent_id 一列。
        for (ProjectExport.ExportEntity e : entities) {
            if (e.parentId() == null) {
                continue;
            }
            Long newId = entityIdMap.get(e.id());
            Long newParentId = entityIdMap.get(e.parentId());
            entityMapper.update(new LambdaUpdateWrapper<SimEntity>()
                    .eq(SimEntity::getId, newId)
                    .set(SimEntity::getParentId, newParentId));
        }

        // 4) 关系
        for (ProjectExport.ExportRelation r : nullSafe(data.relations())) {
            Relation row = new Relation();
            row.setProjectId(projectId);
            row.setTemplateId(relationTemplateIdMap.get(r.templateId()));
            row.setFromEntityId(entityIdMap.get(r.fromEntityId()));
            row.setToEntityId(entityIdMap.get(r.toEntityId()));
            row.setRemark(r.remark());
            relationMapper.insert(row);
        }

        // 5) 产出物元信息（entity_id 按映射改写；对象仍指向原 object_key）
        for (ProjectExport.ExportAsset a : nullSafe(data.assets())) {
            Asset row = new Asset();
            row.setEntityId(entityIdMap.get(a.entityId()));
            row.setAssetType(a.assetType());
            row.setFileName(a.fileName());
            row.setObjectKey(a.objectKey());
            row.setContentText(a.contentText());
            row.setSize(a.size());
            row.setMimeType(a.mimeType());
            assetMapper.insert(row);
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** 把 "[1,2,3]" 形式的旧模板 id 数组按映射改写为新 id 数组；空/null 原样返回。 */
    private String remapIdArray(String json, Map<Long, Long> idMap) {
        if (json == null || json.isBlank()) {
            return json;
        }
        String trimmed = json.trim();
        String inner = trimmed.replaceAll("^\\[|]$", "").trim();
        if (inner.isEmpty()) {
            return "[]";
        }
        List<String> mapped = new ArrayList<>();
        for (String part : inner.split(",")) {
            String tok = part.trim();
            if (tok.isEmpty()) {
                continue;
            }
            Long oldId = Long.valueOf(tok);
            Long newId = idMap.get(oldId);
            mapped.add(String.valueOf(newId != null ? newId : oldId));
        }
        return "[" + String.join(",", mapped) + "]";
    }
}
