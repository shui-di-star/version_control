package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 产出物表 t_asset。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_asset")
public class Asset extends BaseEntity {

    private Long entityId;
    /** 类型：PPT/DOC/SHEET/IMAGE/ANIMATION/TEXT。 */
    private String assetType;
    private String fileName;
    /** MinIO 对象 key（TEXT 内联时可空）。 */
    private String objectKey;
    /** 文字类产出物内联内容（TEXT 类型时用）。 */
    private String contentText;
    private Long size;
    private String mimeType;

    @TableLogic
    private Integer deleted;
}
