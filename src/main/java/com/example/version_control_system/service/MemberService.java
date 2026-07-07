package com.example.version_control_system.service;

import com.example.version_control_system.dto.MemberAddRequest;
import com.example.version_control_system.dto.MemberVO;

import java.util.List;

/** 项目成员服务：列表、添加/分配角色、移除。 */
public interface MemberService {

    /** 列出项目全部成员（含用户信息与角色）。 */
    List<MemberVO> list(Long projectId);

    /** 添加成员并分配角色；用户须存在，且不能重复添加。 */
    MemberVO add(Long projectId, MemberAddRequest request);

    /** 移除成员；不能移除项目 owner。 */
    void remove(Long projectId, Long userId);
}
