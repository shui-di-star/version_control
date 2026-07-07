package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 添加/分配项目成员请求。按用户名添加（用户不需知道对方雪花 id）；role ∈ ADMIN/EDITOR/VIEWER。 */
public record MemberAddRequest(
        @NotBlank String username,
        @NotBlank @Pattern(regexp = "ADMIN|EDITOR|VIEWER", message = "role 必须为 ADMIN/EDITOR/VIEWER") String role) {
}
