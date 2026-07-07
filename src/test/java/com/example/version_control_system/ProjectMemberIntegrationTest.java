package com.example.version_control_system;

import com.example.version_control_system.dto.MemberAddRequest;
import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.dto.ProjectUpdateRequest;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import com.example.version_control_system.mapper.UserMapper;
import com.example.version_control_system.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段四：项目 CRUD（4.1）、成员管理（4.2）、跨项目数据隔离（4.3）集成测试。
 * 连 vcs_test，@Transactional 回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectMemberIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired ProjectMemberMapper projectMemberMapper;
    @Autowired JwtService jwtService;

    /** 建用户并返回 {id, token}。 */
    private long[] userIdHolder = new long[1];
    private String lastUsername;

    private String createUserAndToken(String systemRole) {
        User u = new User();
        u.setUsername("u_" + System.nanoTime());
        u.setPasswordHash("$2a$x");
        u.setSystemRole(systemRole);
        u.setStatus(1);
        userMapper.insert(u);
        userIdHolder[0] = u.getId();
        lastUsername = u.getUsername();
        return jwtService.generateToken(u.getId(), u.getUsername());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ===== 4.1 项目 CRUD =====

    @Test
    void create_makesCreatorAdminMember() throws Exception {
        String token = createUserAndToken("USER");
        long creatorId = userIdHolder[0];
        ProjectCreateRequest req = new ProjectCreateRequest("P-" + System.nanoTime(), "desc");
        MvcResult result = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.myRole").value("ADMIN"))
                .andReturn();
        long projectId = readProjectId(result);
        // 创建者在 t_project_member 中角色为 ADMIN
        ProjectMember m = projectMemberMapper.selectList(null).stream()
                .filter(pm -> pm.getProjectId().equals(projectId) && pm.getUserId().equals(creatorId))
                .findFirst().orElseThrow();
        assertThat(m.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void list_returnsOnlyMyProjectsWithRole() throws Exception {
        String tokenA = createUserAndToken("USER");
        // A 创建一个项目
        createProject(tokenA);
        // A 的列表含该项目且带角色
        mockMvc.perform(get("/api/projects").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].myRole").value("ADMIN"));

        // 另一用户 B 未参与，列表为空
        String tokenB = createUserAndToken("USER");
        mockMvc.perform(get("/api/projects").header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void update_byAdmin_ok() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        ProjectUpdateRequest req = new ProjectUpdateRequest("newName", "newDesc");
        mockMvc.perform(put("/api/projects/{id}", projectId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("newName"));
    }

    @Test
    void delete_byNonOwner_forbidden() throws Exception {
        String ownerToken = createUserAndToken("USER");
        long projectId = createProject(ownerToken);
        // 把另一个用户加为 ADMIN 成员（非 owner）
        String adminToken = createUserAndToken("USER");
        long adminId = userIdHolder[0];
        addMember(projectId, adminId, "ADMIN");
        // 非 owner 的 Admin 删除项目 → 业务拒绝（owner 校验，FORBIDDEN 2001）
        mockMvc.perform(delete("/api/projects/{id}", projectId).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void delete_byOwner_ok() throws Exception {
        String ownerToken = createUserAndToken("USER");
        long projectId = createProject(ownerToken);
        mockMvc.perform(delete("/api/projects/{id}", projectId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ===== 4.2 成员管理 =====

    @Test
    void addMember_byAdmin_ok_andDuplicateRejected() throws Exception {
        String adminToken = createUserAndToken("USER");
        long projectId = createProject(adminToken);
        createUserAndToken("USER");
        String memberUsername = lastUsername;

        MemberAddRequest req = new MemberAddRequest(memberUsername, "EDITOR");
        mockMvc.perform(post("/api/projects/{id}/members", projectId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("EDITOR"));
        // 重复添加同一成员 → 冲突 3001
        mockMvc.perform(post("/api/projects/{id}/members", projectId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    void addMember_byEditor_forbidden() throws Exception {
        String adminToken = createUserAndToken("USER");
        long projectId = createProject(adminToken);
        // Editor 成员
        String editorToken = createUserAndToken("USER");
        long editorId = userIdHolder[0];
        addMember(projectId, editorId, "EDITOR");
        // 待添加的第三个用户
        createUserAndToken("USER");
        String targetUsername = lastUsername;

        MemberAddRequest req = new MemberAddRequest(targetUsername, "VIEWER");
        // Editor 调用成员写操作 → 403（角色不足）
        mockMvc.perform(post("/api/projects/{id}/members", projectId).header("Authorization", bearer(editorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void listMembers_byViewer_ok() throws Exception {
        String adminToken = createUserAndToken("USER");
        long projectId = createProject(adminToken);
        String viewerToken = createUserAndToken("USER");
        long viewerId = userIdHolder[0];
        addMember(projectId, viewerId, "VIEWER");
        // Viewer 可查看成员列表（含 admin 与自己两条）
        mockMvc.perform(get("/api/projects/{id}/members", projectId).header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void removeMember_byAdmin_ok() throws Exception {
        String adminToken = createUserAndToken("USER");
        long projectId = createProject(adminToken);
        String memberToken = createUserAndToken("USER");
        long memberId = userIdHolder[0];
        addMember(projectId, memberId, "EDITOR");
        mockMvc.perform(delete("/api/projects/{id}/members/{uid}", projectId, memberId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ===== 4.3 跨项目数据隔离 =====

    @Test
    void crossProject_accessMembers_forbidden() throws Exception {
        // 用户 A 建项目
        String tokenA = createUserAndToken("USER");
        long projectA = createProject(tokenA);
        // 用户 B 未参与项目 A
        String tokenB = createUserAndToken("USER");
        // B 访问项目 A 的成员列表 → 403（非成员）
        mockMvc.perform(get("/api/projects/{id}/members", projectA).header("Authorization", bearer(tokenB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2001));
        // B 试图编辑项目 A → 403（非成员）
        ProjectUpdateRequest req = new ProjectUpdateRequest("hack", null);
        mockMvc.perform(put("/api/projects/{id}", projectA).header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void superAdmin_canDeleteAnyProject() throws Exception {
        String ownerToken = createUserAndToken("USER");
        long projectId = createProject(ownerToken);
        // SuperAdmin 非 owner 也能删
        String superToken = createUserAndToken("SUPER_ADMIN");
        mockMvc.perform(delete("/api/projects/{id}", projectId).header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---- helpers ----
    private long createProject(String token) throws Exception {
        ProjectCreateRequest req = new ProjectCreateRequest("P-" + System.nanoTime(), "d");
        MvcResult result = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return readProjectId(result);
    }

    private long readProjectId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    private void addMember(long projectId, long userId, String role) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(role);
        projectMemberMapper.insert(m);
    }
}
