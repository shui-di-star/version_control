package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 属性图片引用计数表。同一 objectKey 可被多个实体引用（复制卡片场景），
 * 删除实体时 ref_count--，仅当 ref_count<=0 时才真正删除 MinIO 对象。
 */
@Data
@TableName("t_attr_image_ref")
public class AttrImageRef {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    private String objectKey;

    private Integer refCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
