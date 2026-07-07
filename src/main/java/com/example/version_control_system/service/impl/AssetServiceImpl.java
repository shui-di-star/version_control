package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.AssetDownload;
import com.example.version_control_system.dto.AssetVO;
import com.example.version_control_system.entity.Asset;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.AssetMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.AssetService;
import com.example.version_control_system.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** 产出物服务实现：归属校验 + 大小上限 + MinIO 存储 + 元信息落库。 */
@Service
public class AssetServiceImpl implements AssetService {

    /** 单文件上限 100MB（决策/计划步骤 1.4）。 */
    private static final long MAX_SIZE = 100L * 1024 * 1024;

    private final AssetMapper assetMapper;
    private final SimEntityMapper entityMapper;
    private final StorageService storageService;

    public AssetServiceImpl(AssetMapper assetMapper,
                            SimEntityMapper entityMapper,
                            StorageService storageService) {
        this.assetMapper = assetMapper;
        this.entityMapper = entityMapper;
        this.storageService = storageService;
    }

    @Override
    @Transactional
    public AssetVO upload(Long projectId, Long entityId, String assetType, MultipartFile file) {
        requireEntity(projectId, entityId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件超过 100MB 上限");
        }
        String objectKey = "p" + projectId + "/e" + entityId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        try {
            storageService.upload(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取上传文件失败：" + e.getMessage());
        }
        Asset asset = new Asset();
        asset.setEntityId(entityId);
        asset.setAssetType(assetType);
        asset.setFileName(file.getOriginalFilename());
        asset.setObjectKey(objectKey);
        asset.setSize(file.getSize());
        asset.setMimeType(file.getContentType());
        assetMapper.insert(asset);
        return AssetVO.from(asset);
    }

    @Override
    @Transactional
    public AssetVO uploadText(Long projectId, Long entityId, String fileName, String contentText) {
        requireEntity(projectId, entityId);
        Asset asset = new Asset();
        asset.setEntityId(entityId);
        asset.setAssetType("TEXT");
        asset.setFileName(fileName);
        asset.setContentText(contentText);
        asset.setSize(contentText == null ? 0L : (long) contentText.getBytes().length);
        asset.setMimeType("text/plain");
        assetMapper.insert(asset);
        return AssetVO.from(asset);
    }

    @Override
    public List<AssetVO> list(Long projectId, Long entityId) {
        requireEntity(projectId, entityId);
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>().eq(Asset::getEntityId, entityId));
        return assets.stream().map(AssetVO::from).toList();
    }

    @Override
    public AssetDownload download(Long projectId, Long assetId) {
        Asset asset = requireAsset(projectId, assetId);
        if (asset.getObjectKey() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该产出物为内联文本，无可下载文件");
        }
        return new AssetDownload(storageService.download(asset.getObjectKey()),
                asset.getFileName(), asset.getMimeType(), asset.getSize());
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long assetId) {
        Asset asset = requireAsset(projectId, assetId);
        if (asset.getObjectKey() != null) {
            storageService.delete(asset.getObjectKey());
        }
        assetMapper.deleteById(assetId);
    }

    private SimEntity requireEntity(Long projectId, Long entityId) {
        SimEntity entity = entityMapper.selectById(entityId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实体不存在或不属于本项目");
        }
        return entity;
    }

    /** 产出物须存在且其所属实体在本项目内。 */
    private Asset requireAsset(Long projectId, Long assetId) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产出物不存在");
        }
        requireEntity(projectId, asset.getEntityId());
        return asset;
    }
}
