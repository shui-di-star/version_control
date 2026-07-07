package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户表 t_user。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    private String username;
    private String passwordHash;
    private String email;
    private String displayName;
    /** 全局角色：SUPER_ADMIN / USER。 */
    private String systemRole;
    /** 0 禁用 / 1 启用。 */
    private Integer status;
    /** 登出时间戳：iat 早于此值的 JWT 视为失效（决策 6）。 */
    private LocalDateTime tokenInvalidBefore;

    @TableLogic
    private Integer deleted;
}
