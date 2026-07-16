package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.EdgeRemarkImageVO;
import com.example.version_control_system.dto.EdgeRemarkVO;
import com.example.version_control_system.entity.EdgeRemark;
import com.example.version_control_system.entity.EdgeRemarkImage;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.EdgeRemarkImageMapper;
import com.example.version_control_system.mapper.EdgeRemarkMapper;
import com.example.version_control_system.service.EdgeRemarkService;
import com.example.version_control_system.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 连线备注服务实现。 */
@Service
public class EdgeRemarkServiceImpl implements EdgeRemarkService {

    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024; // 20MB per image

    private final EdgeRemarkMapper remarkMapper;
    private final EdgeRemarkImageMapper imageMapper;
    private final StorageService storageService;

    public EdgeRemarkServiceImpl(EdgeRemarkMapper remarkMapper,
                                 EdgeRemarkImageMapper imageMapper,
                                 StorageService storageService) {
        this.remarkMapper = remarkMapper;
        this.imageMapper = imageMapper;
        this.storageService = storageService;
    }

    @Override
    public List<EdgeRemarkVO> list(Long projectId, Long entityId) {
        List<EdgeRemark> remarks = remarkMapper.selectList(
                new LambdaQueryWrapper<EdgeRemark>()
                        .eq(EdgeRemark::getProjectId, projectId)
                        .eq(EdgeRemark::getEntityId, entityId)
                        .orderByAsc(EdgeRemark::getSortOrder)
                        .orderByAsc(EdgeRemark::getCreatedAt));
        if (remarks.isEmpty()) {
            return List.of();
        }
        List<Long> remarkIds = remarks.stream().map(EdgeRemark::getId).toList();
        List<EdgeRemarkImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<EdgeRemarkImage>()
                        .in(EdgeRemarkImage::getRemarkId, remarkIds)
                        .orderByAsc(EdgeRemarkImage::getSortOrder));
        Map<Long, List<EdgeRemarkImageVO>> imageMap = images.stream()
                .collect(Collectors.groupingBy(EdgeRemarkImage::getRemarkId,
                        Collectors.mapping(this::toImageVO, Collectors.toList())));
        return remarks.stream()
                .map(r -> new EdgeRemarkVO(r.getId(), r.getEntityId(), r.getContent(),
                        r.getSortOrder(), imageMap.getOrDefault(r.getId(), List.of()), r.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public EdgeRemarkVO create(Long projectId, Long entityId, String content) {
        // Determine next sort_order
        Long count = remarkMapper.selectCount(
                new LambdaQueryWrapper<EdgeRemark>()
                        .eq(EdgeRemark::getProjectId, projectId)
                        .eq(EdgeRemark::getEntityId, entityId));
        EdgeRemark remark = new EdgeRemark();
        remark.setProjectId(projectId);
        remark.setEntityId(entityId);
        remark.setContent(content);
        remark.setSortOrder(count.intValue());
        remarkMapper.insert(remark);
        return new EdgeRemarkVO(remark.getId(), remark.getEntityId(), remark.getContent(),
                remark.getSortOrder(), List.of(), remark.getCreatedAt());
    }

    @Override
    @Transactional
    public EdgeRemarkVO update(Long projectId, Long remarkId, String content) {
        EdgeRemark remark = requireRemark(projectId, remarkId);
        remark.setContent(content);
        remarkMapper.updateById(remark);
        List<EdgeRemarkImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<EdgeRemarkImage>()
                        .eq(EdgeRemarkImage::getRemarkId, remarkId)
                        .orderByAsc(EdgeRemarkImage::getSortOrder));
        List<EdgeRemarkImageVO> imageVOs = images.stream().map(this::toImageVO).toList();
        return new EdgeRemarkVO(remark.getId(), remark.getEntityId(), remark.getContent(),
                remark.getSortOrder(), imageVOs, remark.getCreatedAt());
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long remarkId) {
        EdgeRemark remark = requireRemark(projectId, remarkId);
        // Delete images from MinIO
        List<EdgeRemarkImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<EdgeRemarkImage>()
                        .eq(EdgeRemarkImage::getRemarkId, remarkId));
        for (EdgeRemarkImage img : images) {
            try {
                storageService.delete(img.getObjectKey());
            } catch (Exception ignored) {
            }
        }
        imageMapper.delete(new LambdaQueryWrapper<EdgeRemarkImage>()
                .eq(EdgeRemarkImage::getRemarkId, remarkId));
        remarkMapper.deleteById(remarkId);
    }

    @Override
    @Transactional
    public EdgeRemarkImageVO uploadImage(Long projectId, Long remarkId, MultipartFile file) {
        EdgeRemark remark = requireRemark(projectId, remarkId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片超过 20MB 上限");
        }
        String objectKey = "p" + projectId + "/remark/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        try {
            storageService.upload(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取上传文件失败：" + e.getMessage());
        }
        Long count = imageMapper.selectCount(
                new LambdaQueryWrapper<EdgeRemarkImage>()
                        .eq(EdgeRemarkImage::getRemarkId, remarkId));
        EdgeRemarkImage img = new EdgeRemarkImage();
        img.setRemarkId(remarkId);
        img.setFileName(file.getOriginalFilename());
        img.setObjectKey(objectKey);
        img.setSize(file.getSize());
        img.setMimeType(file.getContentType());
        img.setSortOrder(count.intValue());
        imageMapper.insert(img);
        return toImageVO(img);
    }

    @Override
    @Transactional
    public void deleteImage(Long projectId, Long imageId) {
        EdgeRemarkImage img = imageMapper.selectById(imageId);
        if (img == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        // Verify project ownership via remark
        EdgeRemark remark = remarkMapper.selectById(img.getRemarkId());
        if (remark == null || !remark.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        try {
            storageService.delete(img.getObjectKey());
        } catch (Exception ignored) {
        }
        imageMapper.deleteById(imageId);
    }

    @Override
    public EdgeRemarkImage getImage(Long projectId, Long imageId) {
        EdgeRemarkImage img = imageMapper.selectById(imageId);
        if (img == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        EdgeRemark remark = remarkMapper.selectById(img.getRemarkId());
        if (remark == null || !remark.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        return img;
    }

    @Override
    @Transactional
    public void deleteByEntityIds(List<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return;
        List<EdgeRemark> remarks = remarkMapper.selectList(
                new LambdaQueryWrapper<EdgeRemark>()
                        .in(EdgeRemark::getEntityId, entityIds));
        if (remarks.isEmpty()) return;
        List<Long> remarkIds = remarks.stream().map(EdgeRemark::getId).toList();
        // Delete images from MinIO
        List<EdgeRemarkImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<EdgeRemarkImage>()
                        .in(EdgeRemarkImage::getRemarkId, remarkIds));
        for (EdgeRemarkImage img : images) {
            try {
                storageService.delete(img.getObjectKey());
            } catch (Exception ignored) {
            }
        }
        imageMapper.delete(new LambdaQueryWrapper<EdgeRemarkImage>()
                .in(EdgeRemarkImage::getRemarkId, remarkIds));
        remarkMapper.delete(new LambdaQueryWrapper<EdgeRemark>()
                .in(EdgeRemark::getEntityId, entityIds));
    }

    private EdgeRemark requireRemark(Long projectId, Long remarkId) {
        EdgeRemark remark = remarkMapper.selectById(remarkId);
        if (remark == null || !remark.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "备注不存在");
        }
        return remark;
    }

    private EdgeRemarkImageVO toImageVO(EdgeRemarkImage img) {
        return new EdgeRemarkImageVO(img.getId(), img.getFileName(), img.getObjectKey(),
                img.getSize(), img.getMimeType());
    }
}
