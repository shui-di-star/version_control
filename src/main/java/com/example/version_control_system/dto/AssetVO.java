package com.example.version_control_system.dto;

import com.example.version_control_system.entity.Asset;

/** 产出物元信息视图（不含二进制内容；TEXT 类型含内联 contentText）。 */
public record AssetVO(Long id, Long entityId, String assetType, String fileName,
                      String objectKey, String contentText, Long size, String mimeType) {

    public static AssetVO from(Asset a) {
        return new AssetVO(a.getId(), a.getEntityId(), a.getAssetType(), a.getFileName(),
                a.getObjectKey(), a.getContentText(), a.getSize(), a.getMimeType());
    }
}
