package com.example.version_control_system.service;

import java.util.List;

/**
 * 属性图片引用计数服务。
 * <p>上传时 addRef；实体删除时 releaseRef（ref_count--，降为0则删除 MinIO 对象）。
 * 复制卡片时对所有图片 key 执行 addRef。</p>
 */
public interface AttrImageRefService {

    /** 增加引用（上传新图片或复制卡片时调用） */
    void addRef(Long projectId, String objectKey);

    /** 批量增加引用 */
    void addRefs(Long projectId, List<String> objectKeys);

    /** 释放引用，ref_count 降为 0 时删除 MinIO 对象 */
    void releaseRef(String objectKey);

    /** 批量释放引用 */
    void releaseRefs(List<String> objectKeys);
}
