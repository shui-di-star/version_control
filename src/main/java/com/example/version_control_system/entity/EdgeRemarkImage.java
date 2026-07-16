package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 连线备注图片。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_edge_remark_image")
public class EdgeRemarkImage extends BaseEntity {

    private Long remarkId;
    private String fileName;
    private String objectKey;
    private Long size;
    private String mimeType;
    private Integer sortOrder;

    @TableLogic
    private Integer deleted;
}
