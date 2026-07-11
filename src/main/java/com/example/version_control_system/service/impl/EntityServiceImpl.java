package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.AttributeValidator;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.ChildStrategy;
import com.example.version_control_system.dto.EntityCreateRequest;
import com.example.version_control_system.dto.EntityReparentRequest;
import com.example.version_control_system.dto.EntityTreeNode;
import com.example.version_control_system.dto.EntityTreeRow;
import com.example.version_control_system.dto.EntityUpdateRequest;
import com.example.version_control_system.dto.EntityVO;
import com.example.version_control_system.entity.Asset;
import com.example.version_control_system.entity.EntityTemplate;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.RelationTemplate;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.AssetMapper;
import com.example.version_control_system.mapper.EntityTemplateMapper;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.RelationTemplateMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.AttrImageRefService;
import com.example.version_control_system.service.EntityService;
import com.example.version_control_system.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 实体核心服务实现：属性校验、单父树装配、CASCADE/PROMOTE 删除、路径追溯、里程碑/状态。 */
@Service
public class EntityServiceImpl implements EntityService {

    private static final Set<String> STATUSES = Set.of("RECOMMENDED", "DEPRECATED", "SIMULATING", "COMPLETED");

    private final SimEntityMapper entityMapper;
    private final EntityTemplateMapper templateMapper;
    private final RelationMapper relationMapper;
    private final RelationTemplateMapper relationTemplateMapper;
    private final AssetMapper assetMapper;
    private final AttributeValidator attributeValidator;
    private final StorageService storageService;
    private final AttrImageRefService attrImageRefService;

    public EntityServiceImpl(SimEntityMapper entityMapper,
                             EntityTemplateMapper templateMapper,
                             RelationMapper relationMapper,
                             RelationTemplateMapper relationTemplateMapper,
                             AssetMapper assetMapper,
                             AttributeValidator attributeValidator,
                             StorageService storageService,
                             AttrImageRefService attrImageRefService) {
        this.entityMapper = entityMapper;
        this.templateMapper = templateMapper;
        this.relationMapper = relationMapper;
        this.relationTemplateMapper = relationTemplateMapper;
        this.assetMapper = assetMapper;
        this.attributeValidator = attributeValidator;
        this.storageService = storageService;
        this.attrImageRefService = attrImageRefService;
    }

    @Override
    @Transactional
    public EntityVO create(Long projectId, EntityCreateRequest request) {
        EntityTemplate template = requireTemplate(projectId, request.templateId());
        if (request.parentId() != null) {
            requireEntity(projectId, request.parentId());
            // 创建子节点时必须指定关系类型
            if (request.parentRelationTemplateId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "创建子节点时必须指定关系类型");
            }
            requireRelationTemplate(projectId, request.parentRelationTemplateId());
        }
        // 创建阶段实体尚无 id，FILE 归属校验跳过（entityId=null）
        String attributes = attributeValidator.validate(template, request.attributes(), null);

        SimEntity entity = new SimEntity();
        entity.setProjectId(projectId);
        entity.setTemplateId(request.templateId());
        entity.setParentId(request.parentId());
        entity.setName(request.name());
        entity.setRemark(request.remark());
        entity.setAttributes(attributes);
        entity.setIsMilestone(0);
        if (request.parentId() != null) {
            entity.setParentRelationTemplateId(request.parentRelationTemplateId());
            entity.setParentRelationRemark(request.parentRelationRemark());
        }
        entityMapper.insert(entity);
        // 复制卡片时 attributes 中可能已包含图片 key，需增加引用计数
        addAttrImageRefs(projectId, attributes);
        return EntityVO.from(entity);
    }

    @Override
    @Transactional
    public EntityVO update(Long projectId, Long entityId, EntityUpdateRequest request) {
        SimEntity entity = requireEntity(projectId, entityId);
        EntityTemplate template = requireTemplate(projectId, entity.getTemplateId());
        String attributes = attributeValidator.validate(template, request.attributes(), entityId);
        entity.setName(request.name());
        entity.setRemark(request.remark());
        entity.setAttributes(attributes);
        entityMapper.updateById(entity);
        return EntityVO.from(entity);
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long entityId, ChildStrategy strategy) {
        SimEntity entity = requireEntity(projectId, entityId);
        if (strategy == ChildStrategy.CASCADE) {
            deleteCascade(entityId);
        } else {
            deletePromote(entity);
        }
    }

    /** 递归软删子树全部实体，并清理其关联 relation/asset。 */
    private void deleteCascade(Long rootId) {
        List<Long> ids = entityMapper.selectSubtreeIds(rootId);
        if (ids.isEmpty()) {
            return;
        }
        deleteRelationsAndAssets(ids);
        entityMapper.deleteByIds(ids);
    }

    /** 仅删本节点，直接子节点 parent_id 上提至被删节点的父节点。 */
    private void deletePromote(SimEntity entity) {
        List<SimEntity> children = entityMapper.selectList(new LambdaQueryWrapper<SimEntity>()
                .eq(SimEntity::getParentId, entity.getId()));
        for (SimEntity child : children) {
            child.setParentId(entity.getParentId());
            entityMapper.updateById(child);
        }
        deleteRelationsAndAssets(List.of(entity.getId()));
        entityMapper.deleteById(entity.getId());
    }

    /** 删除给定实体 id 集合关联的语义关系（from/to 命中）与产出物（含 MinIO 对象），以及属性图片。 */
    private void deleteRelationsAndAssets(List<Long> entityIds) {
        relationMapper.delete(new LambdaQueryWrapper<Relation>().in(Relation::getFromEntityId, entityIds));
        relationMapper.delete(new LambdaQueryWrapper<Relation>().in(Relation::getToEntityId, entityIds));
        // 先查出所有 asset 的 objectKey，清理 MinIO 中的文件
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>().in(Asset::getEntityId, entityIds));
        for (Asset asset : assets) {
            if (asset.getObjectKey() != null && !asset.getObjectKey().isBlank()) {
                try {
                    storageService.delete(asset.getObjectKey());
                } catch (Exception ignored) {
                }
            }
        }
        assetMapper.delete(new LambdaQueryWrapper<Asset>().in(Asset::getEntityId, entityIds));

        // 清理属性图片（存在 attributes JSON 中，路径以 p{pid}/attr/ 开头）
        List<SimEntity> entities = entityMapper.selectByIds(entityIds);
        for (SimEntity entity : entities) {
            deleteAttrImages(entity);
        }
    }

    /** 从实体 attributes 中解析 IMAGE 类型字段值（objectKey），通过引用计数释放。 */
    private void deleteAttrImages(SimEntity entity) {
        List<String> keys = extractAttrImageKeys(entity.getAttributes());
        if (!keys.isEmpty()) {
            attrImageRefService.releaseRefs(keys);
        }
    }

    /** 为实体 attributes 中已有的图片 key 增加引用（复制卡片场景）。 */
    private void addAttrImageRefs(Long projectId, String attributes) {
        List<String> keys = extractAttrImageKeys(attributes);
        if (!keys.isEmpty()) {
            attrImageRefService.addRefs(projectId, keys);
        }
    }

    /** 从 attributes JSON 中提取所有属性图片 objectKey。 */
    private List<String> extractAttrImageKeys(String attrs) {
        if (attrs == null || !attrs.contains("/attr/")) return List.of();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"(p\\d+/attr/[^\"]+)\"")
                .matcher(attrs);
        List<String> keys = new ArrayList<>();
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    @Override
    public List<EntityTreeNode> tree(Long projectId) {
        List<EntityTreeRow> rows = entityMapper.selectTree(projectId);
        Map<Long, EntityTreeNode> nodes = new LinkedHashMap<>();
        for (EntityTreeRow row : rows) {
            nodes.put(row.id(), EntityTreeNode.of(row));
        }
        List<EntityTreeNode> roots = new ArrayList<>();
        for (EntityTreeNode node : nodes.values()) {
            EntityTreeNode parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    @Override
    public EntityVO get(Long projectId, Long entityId) {
        return EntityVO.from(requireEntity(projectId, entityId));
    }

    @Override
    public List<EntityVO> path(Long projectId, Long entityId) {
        requireEntity(projectId, entityId);
        List<EntityTreeRow> rows = entityMapper.selectPathToRoot(entityId);
        // CTE 自当前向根，反转为根 → 当前
        Collections.reverse(rows);
        List<EntityVO> result = new ArrayList<>();
        for (EntityTreeRow row : rows) {
            SimEntity e = entityMapper.selectById(row.id());
            if (e != null) {
                result.add(EntityVO.from(e));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public EntityVO toggleMilestone(Long projectId, Long entityId) {
        SimEntity entity = requireEntity(projectId, entityId);
        int current = entity.getIsMilestone() == null ? 0 : entity.getIsMilestone();
        entity.setIsMilestone(current == 0 ? 1 : 0);
        entityMapper.updateById(entity);
        return EntityVO.from(entity);
    }

    @Override
    @Transactional
    public EntityVO setStatus(Long projectId, Long entityId, String status) {
        SimEntity entity = requireEntity(projectId, entityId);
        String normalized = status == null || status.isBlank() ? null : status;
        if (normalized != null && !STATUSES.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法状态：" + status);
        }
        entity.setStatus(normalized);
        entityMapper.updateById(entity);
        return EntityVO.from(entity);
    }

    private EntityTemplate requireTemplate(Long projectId, Long templateId) {
        EntityTemplate template = templateMapper.selectById(templateId);
        if (template == null || !template.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "实体模板不存在或不属于本项目");
        }
        return template;
    }

    @Override
    @Transactional
    public EntityVO reparent(Long projectId, Long entityId, EntityReparentRequest request) {
        SimEntity entity = requireEntity(projectId, entityId);

        if (request.parentId() != null) {
            // 新父节点须存在且属于本项目
            requireEntity(projectId, request.parentId());
            // 防环：新父不能是当前节点的后代
            List<Long> subtreeIds = entityMapper.selectSubtreeIds(entityId);
            if (subtreeIds.contains(request.parentId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不能将节点设为自己后代的子节点（会产生环）");
            }
            // 关系类型必填
            if (request.parentRelationTemplateId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "重选父节点时必须指定关系类型");
            }
            requireRelationTemplate(projectId, request.parentRelationTemplateId());
            entity.setParentId(request.parentId());
            entity.setParentRelationTemplateId(request.parentRelationTemplateId());
            entity.setParentRelationRemark(request.parentRelationRemark());
        } else {
            // 变为根节点
            entity.setParentId(null);
            entity.setParentRelationTemplateId(null);
            entity.setParentRelationRemark(null);
        }
        entityMapper.updateById(entity);
        return EntityVO.from(entity);
    }

    private SimEntity requireEntity(Long projectId, Long entityId) {
        SimEntity entity = entityMapper.selectById(entityId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实体不存在");
        }
        return entity;
    }

    private RelationTemplate requireRelationTemplate(Long projectId, Long templateId) {
        RelationTemplate rt = relationTemplateMapper.selectById(templateId);
        if (rt == null || !rt.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "关系模板不存在或不属于本项目");
        }
        return rt;
    }
}