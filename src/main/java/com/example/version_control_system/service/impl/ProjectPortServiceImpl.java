package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.version_control_system.dto.ProjectExport;
import com.example.version_control_system.entity.Asset;
import com.example.version_control_system.entity.EdgeRemark;
import com.example.version_control_system.entity.EdgeRemarkImage;
import com.example.version_control_system.entity.EntityTemplate;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.RelationTemplate;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.mapper.AssetMapper;
import com.example.version_control_system.mapper.EdgeRemarkImageMapper;
import com.example.version_control_system.mapper.EdgeRemarkMapper;
import com.example.version_control_system.mapper.EntityTemplateMapper;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.RelationTemplateMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.AttrImageRefService;
import com.example.version_control_system.service.ProjectPortService;
import com.example.version_control_system.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final EdgeRemarkMapper edgeRemarkMapper;
    private final EdgeRemarkImageMapper edgeRemarkImageMapper;
    private final StorageService storageService;
    private final AttrImageRefService attrImageRefService;

    public ProjectPortServiceImpl(EntityTemplateMapper entityTemplateMapper,
                                  RelationTemplateMapper relationTemplateMapper,
                                  SimEntityMapper entityMapper,
                                  RelationMapper relationMapper,
                                  AssetMapper assetMapper,
                                  EdgeRemarkMapper edgeRemarkMapper,
                                  EdgeRemarkImageMapper edgeRemarkImageMapper,
                                  StorageService storageService,
                                  AttrImageRefService attrImageRefService) {
        this.entityTemplateMapper = entityTemplateMapper;
        this.relationTemplateMapper = relationTemplateMapper;
        this.entityMapper = entityMapper;
        this.relationMapper = relationMapper;
        this.assetMapper = assetMapper;
        this.edgeRemarkMapper = edgeRemarkMapper;
        this.edgeRemarkImageMapper = edgeRemarkImageMapper;
        this.storageService = storageService;
        this.attrImageRefService = attrImageRefService;
    }

    @Override
    public ProjectExport export(Long projectId) {
        List<ProjectExport.ExportEntityTemplate> ets = entityTemplateMapper.selectList(
                        new LambdaQueryWrapper<EntityTemplate>().eq(EntityTemplate::getProjectId, projectId))
                .stream().map(t -> new ProjectExport.ExportEntityTemplate(
                        t.getId(), t.getName(), t.getFieldSchema())).toList();

        List<ProjectExport.ExportRelationTemplate> rts = relationTemplateMapper.selectList(
                        new LambdaQueryWrapper<RelationTemplate>().eq(RelationTemplate::getProjectId, projectId))
                .stream().map(t -> new ProjectExport.ExportRelationTemplate(
                        t.getId(), t.getName(), t.getDirected(), t.getLineStyle(),
                        t.getAllowedFrom(), t.getAllowedTo())).toList();

        List<SimEntity> entityRows = entityMapper.selectList(
                new LambdaQueryWrapper<SimEntity>().eq(SimEntity::getProjectId, projectId));
        List<ProjectExport.ExportEntity> entities = entityRows.stream().map(e ->
                new ProjectExport.ExportEntity(e.getId(), e.getTemplateId(), e.getParentId(), e.getName(),
                        e.getStatus(), e.getIsMilestone(), e.getRemark(), e.getAttributes(),
                        e.getParentRelationTemplateId())).toList();

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

        // 连线备注
        List<EdgeRemark> remarkRows = edgeRemarkMapper.selectList(
                new LambdaQueryWrapper<EdgeRemark>().eq(EdgeRemark::getProjectId, projectId));
        List<ProjectExport.ExportEdgeRemark> edgeRemarks = remarkRows.stream().map(r ->
                new ProjectExport.ExportEdgeRemark(r.getId(), r.getEntityId(), r.getContent(), r.getSortOrder())).toList();

        // 连线备注图片
        List<Long> remarkIds = remarkRows.stream().map(EdgeRemark::getId).toList();
        List<ProjectExport.ExportEdgeRemarkImage> edgeRemarkImages = remarkIds.isEmpty() ? List.of()
                : edgeRemarkImageMapper.selectList(new LambdaQueryWrapper<EdgeRemarkImage>().in(EdgeRemarkImage::getRemarkId, remarkIds))
                .stream().map(img -> new ProjectExport.ExportEdgeRemarkImage(img.getId(), img.getRemarkId(),
                        img.getFileName(), img.getObjectKey(), img.getSize(), img.getMimeType(), img.getSortOrder())).toList();

        return new ProjectExport(ets, rts, entities, relations, assets, edgeRemarks, edgeRemarkImages);
    }

    @Override
    @Transactional
    public void importInto(Long projectId, ProjectExport data) {
        // 前置检查：只有空项目才能导入
        long entityCount = entityMapper.selectCount(new LambdaQueryWrapper<SimEntity>()
                .eq(SimEntity::getProjectId, projectId));
        if (entityCount > 0) {
            throw new com.example.version_control_system.exception.BusinessException(
                    com.example.version_control_system.common.ResultCode.BAD_REQUEST,
                    "只能向空项目导入数据，当前项目已有实体");
        }

        Map<Long, Long> entityTemplateIdMap = new HashMap<>();
        Map<Long, Long> relationTemplateIdMap = new HashMap<>();
        Map<Long, Long> entityIdMap = new HashMap<>();

        // 0) 删除现有实体模板和关系模板（覆盖预设）
        entityTemplateMapper.delete(new LambdaQueryWrapper<EntityTemplate>()
                .eq(EntityTemplate::getProjectId, projectId));
        relationTemplateMapper.delete(new LambdaQueryWrapper<RelationTemplate>()
                .eq(RelationTemplate::getProjectId, projectId));

        // 1) 实体模板
        for (ProjectExport.ExportEntityTemplate t : nullSafe(data.entityTemplates())) {
            EntityTemplate row = new EntityTemplate();
            row.setProjectId(projectId);
            row.setName(t.name());
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
        //    同时复制属性图片到新项目路径下
        List<ProjectExport.ExportEntity> entities = nullSafe(data.entities());
        Pattern attrImagePattern = Pattern.compile("\"(p\\d+/attr/[^\"]+)\"");
        for (ProjectExport.ExportEntity e : entities) {
            // 复制属性图片并改写 attributes 中的 objectKey
            String attributes = e.attributes();
            if (attributes != null && attributes.contains("/attr/")) {
                Matcher m = attrImagePattern.matcher(attributes);
                StringBuilder sb = new StringBuilder();
                List<String> newImageKeys = new ArrayList<>();
                while (m.find()) {
                    String oldKey = m.group(1);
                    String newKey = "p" + projectId + "/attr/" + UUID.randomUUID() + "-" + extractFileName(oldKey);
                    String copied = storageService.copy(oldKey, newKey);
                    if (copied != null) {
                        m.appendReplacement(sb, "\"" + Matcher.quoteReplacement(newKey) + "\"");
                        newImageKeys.add(newKey);
                    } else {
                        // 源文件不存在，保留原值（不可见但不破坏数据结构）
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                    }
                }
                m.appendTail(sb);
                attributes = sb.toString();
                // 为复制的图片建立引用计数
                if (!newImageKeys.isEmpty()) {
                    attrImageRefService.addRefs(projectId, newImageKeys);
                }
            }

            SimEntity row = new SimEntity();
            row.setProjectId(projectId);
            row.setTemplateId(entityTemplateIdMap.get(e.templateId()));
            row.setParentId(null);
            row.setName(e.name());
            row.setStatus(e.status());
            row.setIsMilestone(e.isMilestone());
            row.setRemark(e.remark());
            row.setAttributes(attributes);
            row.setParentRelationTemplateId(e.parentRelationTemplateId() != null
                    ? relationTemplateIdMap.get(e.parentRelationTemplateId()) : null);
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

        // 5) 产出物：复制 MinIO 对象到新项目路径下，使导入项目独立于源项目
        for (ProjectExport.ExportAsset a : nullSafe(data.assets())) {
            Long newEntityId = entityIdMap.get(a.entityId());
            String newObjectKey = null;
            if (a.objectKey() != null && !a.objectKey().isBlank()) {
                newObjectKey = "p" + projectId + "/e" + newEntityId + "/" + UUID.randomUUID() + "-" + extractFileName(a.objectKey());
                String copied = storageService.copy(a.objectKey(), newObjectKey);
                if (copied == null) {
                    // 源文件不存在，仍保存元信息但 objectKey 置为原值（下载时会报错）
                    newObjectKey = a.objectKey();
                }
            }
            Asset row = new Asset();
            row.setEntityId(newEntityId);
            row.setAssetType(a.assetType());
            row.setFileName(a.fileName());
            row.setObjectKey(newObjectKey);
            row.setContentText(a.contentText());
            row.setSize(a.size());
            row.setMimeType(a.mimeType());
            assetMapper.insert(row);
        }

        // 6) 连线备注
        Map<Long, Long> edgeRemarkIdMap = new HashMap<>();
        for (ProjectExport.ExportEdgeRemark er : nullSafe(data.edgeRemarks())) {
            Long newEntityId = entityIdMap.get(er.entityId());
            if (newEntityId == null) continue;
            EdgeRemark row = new EdgeRemark();
            row.setEntityId(newEntityId);
            row.setProjectId(projectId);
            row.setContent(er.content());
            row.setSortOrder(er.sortOrder());
            edgeRemarkMapper.insert(row);
            edgeRemarkIdMap.put(er.id(), row.getId());
        }

        // 7) 连线备注图片：复制 MinIO 对象到新路径
        for (ProjectExport.ExportEdgeRemarkImage img : nullSafe(data.edgeRemarkImages())) {
            Long newRemarkId = edgeRemarkIdMap.get(img.remarkId());
            if (newRemarkId == null) continue;
            String newObjectKey = null;
            if (img.objectKey() != null && !img.objectKey().isBlank()) {
                newObjectKey = "p" + projectId + "/edge-remark/" + UUID.randomUUID() + "-" + extractFileName(img.objectKey());
                String copied = storageService.copy(img.objectKey(), newObjectKey);
                if (copied == null) {
                    newObjectKey = img.objectKey();
                }
            }
            EdgeRemarkImage row = new EdgeRemarkImage();
            row.setRemarkId(newRemarkId);
            row.setFileName(img.fileName());
            row.setObjectKey(newObjectKey);
            row.setSize(img.size());
            row.setMimeType(img.mimeType());
            row.setSortOrder(img.sortOrder());
            edgeRemarkImageMapper.insert(row);
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

    /** 从 objectKey 路径中提取文件名部分（最后一个 / 后面，去掉 UUID 前缀）。 */
    private static String extractFileName(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        String name = lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
        // objectKey 格式: uuid-originalFilename，取 uuid 后面的部分作为文件名
        int dash = name.indexOf('-');
        return dash >= 0 ? name.substring(dash + 1) : name;
    }
}
