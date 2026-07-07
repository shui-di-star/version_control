package com.example.version_control_system;

import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import com.example.version_control_system.mapper.UserMapper;
import com.example.version_control_system.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 步骤 3.5：@RequireProjectRole 切面集成测试。
 * 覆盖：SuperAdmin 放行 / 项目成员角色达标放行 / 角色不足 403 / 非成员 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectRoleAspectTest {

    private static final Long PROJECT_ID = 555L;

    @Autowired MockMvc mockMvc;
    @Autowired UserMapper userMapper;
    @Autowired ProjectMemberMapper projectMemberMapper;
    @Autowired JwtService jwtService;

    private String createUserAndToken(String systemRole) {
        User u = new User();
        u.setUsername("u_" + System.nanoTime());
        u.setPasswordHash("$2a$x");
        u.setSystemRole(systemRole);
        u.setStatus(1);
        userMapper.insert(u);
        return jwtService.generateToken(u.getId(), u.getUsername());
    }

    private void addMember(String token, String role) {
        Long userId = jwtService.getUserId(jwtService.parse(token));
        ProjectMember m = new ProjectMember();
        m.setProjectId(PROJECT_ID);
        m.setUserId(userId);
        m.setRole(role);
        projectMemberMapper.insert(m);
    }

    @Test
    void superAdmin_bypasses_evenWithoutMembership() throws Exception {
        String token = createUserAndToken("SUPER_ADMIN");
        // 不加成员记录，SuperAdmin 也应放行 admin-op
        mockMvc.perform(get("/test/projects/{pid}/admin-op", PROJECT_ID).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void memberWithSufficientRole_passes() throws Exception {
        String token = createUserAndToken("USER");
        addMember(token, "EDITOR");
        // EDITOR 满足 editor-op 与 viewer-op
        mockMvc.perform(get("/test/projects/{pid}/editor-op", PROJECT_ID).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/test/projects/{pid}/viewer-op", PROJECT_ID).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void memberWithInsufficientRole_forbidden() throws Exception {
        String token = createUserAndToken("USER");
        addMember(token, "VIEWER");
        // VIEWER 不满足 admin-op → 403
        mockMvc.perform(get("/test/projects/{pid}/admin-op", PROJECT_ID).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void nonMember_forbidden() throws Exception {
        String token = createUserAndToken("USER");
        // 不加成员记录 → 非项目成员 → 403
        mockMvc.perform(get("/test/projects/{pid}/viewer-op", PROJECT_ID).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2001));
    }
}
