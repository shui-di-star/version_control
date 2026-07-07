package com.example.version_control_system.service;

import com.example.version_control_system.dto.RelationCreateRequest;
import com.example.version_control_system.dto.RelationUpdateRequest;
import com.example.version_control_system.dto.RelationVO;

import java.util.List;

/** 语义关系服务：非父子的额外关系（引用/派生等）。 */
public interface RelationService {

    List<RelationVO> list(Long projectId);

    RelationVO get(Long projectId, Long relationId);

    RelationVO create(Long projectId, RelationCreateRequest request);

    RelationVO update(Long projectId, Long relationId, RelationUpdateRequest request);

    void delete(Long projectId, Long relationId);
}
