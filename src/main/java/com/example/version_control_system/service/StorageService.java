package com.example.version_control_system.service;

import java.io.InputStream;

/** 对象存储抽象：封装 MinIO 上传/下载/删除。 */
public interface StorageService {

    /** 上传对象，返回 object key。 */
    String upload(String objectKey, InputStream stream, long size, String contentType);

    /** 下载对象为输入流（调用方负责关闭）。 */
    InputStream download(String objectKey);

    /** 删除对象。 */
    void delete(String objectKey);
}
