package com.example.version_control_system.service;

import com.example.version_control_system.dto.EdgeRemarkImageVO;
import com.example.version_control_system.dto.EdgeRemarkVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 连线备注服务：CRUD + 图片上传/删除 + 级联清理。 */
public interface EdgeRemarkService {

    List<EdgeRemarkVO> list(Long projectId, Long entityId);

    EdgeRemarkVO create(Long projectId, Long entityId, String content);

    EdgeRemarkVO update(Long projectId, Long remarkId, String content);

    void delete(Long projectId, Long remarkId);

    EdgeRemarkImageVO uploadImage(Long projectId, Long remarkId, MultipartFile file);

    void deleteImage(Long projectId, Long imageId);

    /** 获取图片实体（含归属校验）。 */
    com.example.version_control_system.entity.EdgeRemarkImage getImage(Long projectId, Long imageId);

    /** 级联删除指定实体集合的所有备注及其图片（供实体删除时调用）。 */
    void deleteByEntityIds(List<Long> entityIds);
}
