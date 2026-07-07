package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.MemberAddRequest;
import com.example.version_control_system.dto.MemberVO;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 成员接口：列表对成员开放；增/删需项目 Admin。 */
@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<MemberVO>> list(@PathVariable("projectId") Long projectId) {
        return Result.success(memberService.list(projectId));
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<MemberVO> add(@PathVariable("projectId") Long projectId,
                                @Valid @RequestBody MemberAddRequest request) {
        return Result.success(memberService.add(projectId, request));
    }

    @DeleteMapping("/{uid}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<Void> remove(@PathVariable("projectId") Long projectId,
                               @PathVariable("uid") Long uid) {
        memberService.remove(projectId, uid);
        return Result.success();
    }
}
