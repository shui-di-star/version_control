package com.example.version_control_system.service;

import com.example.version_control_system.dto.AssetDownload;
import com.example.version_control_system.dto.AssetVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 产出物服务：上传（MinIO）/列表/下载/删除。 */
public interface AssetService {

    AssetVO upload(Long projectId, Long entityId, String assetType, MultipartFile file);

    /** TEXT 类型内联上传（不走 MinIO）。 */
    AssetVO uploadText(Long projectId, Long entityId, String fileName, String contentText);

    List<AssetVO> list(Long projectId, Long entityId);

    AssetDownload download(Long projectId, Long assetId);

    void delete(Long projectId, Long assetId);
}
