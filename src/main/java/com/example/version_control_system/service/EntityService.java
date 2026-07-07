package com.example.version_control_system.service;

import com.example.version_control_system.dto.ChildStrategy;
import com.example.version_control_system.dto.EntityCreateRequest;
import com.example.version_control_system.dto.EntityReparentRequest;
import com.example.version_control_system.dto.EntityTreeNode;
import com.example.version_control_system.dto.EntityUpdateRequest;
import com.example.version_control_system.dto.EntityVO;

import java.util.List;

/** 实体（迭代节点）服务：创建/更新/删除/树查询/路径/里程碑/状态。 */
public interface EntityService {

    EntityVO create(Long projectId, EntityCreateRequest request);

    EntityVO update(Long projectId, Long entityId, EntityUpdateRequest request);

    /** 删除实体，按策略处理子节点（CASCADE 递归软删 / PROMOTE 子节点上提）。 */
    void delete(Long projectId, Long entityId, ChildStrategy strategy);

    /** 整树（嵌套）；返回根节点列表。 */
    List<EntityTreeNode> tree(Long projectId);

    EntityVO get(Long projectId, Long entityId);

    /** 根 → 当前节点的有序路径。 */
    List<EntityVO> path(Long projectId, Long entityId);

    /** 切换里程碑标记，返回切换后的值。 */
    EntityVO toggleMilestone(Long projectId, Long entityId);

    /** 设置状态（RECOMMENDED/DEPRECATED/SIMULATING/COMPLETED 互斥单选，null/空=清空）。 */
    EntityVO setStatus(Long projectId, Long entityId, String status);

    /** 重选父节点（防环校验）。parentId=null 变为根节点。 */
    EntityVO reparent(Long projectId, Long entityId, EntityReparentRequest request);
}
