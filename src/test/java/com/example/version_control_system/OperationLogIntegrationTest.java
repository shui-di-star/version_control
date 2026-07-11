package com.example.version_control_system;

import com.example.version_control_system.dto.ProjectCreateRequest;
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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段十 10.1：操作日志 AOP 集成测试。
 * 覆盖：创建实体后 t_operation_log 出现对应记录且 detail 含快照；Admin 可查日志、非 Admin（Viewer）被拒。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperationLogIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired ProjectMemberMapper projectMemberMapper;
    @Autowired JwtService jwtService;

    private final long[] userIdHolder = new long[1];

    private String createUserAndToken() {
        User u = new User();
        u.setUsername("u_" + System.nanoTime());
        u.setPasswordHash("$2a$x");
        u.setSystemRole("USER");
        u.setStatus(1);
        userMapper.insert(u);
        userIdHolder[0] = u.getId();
        return jwtService.generateToken(u.getId(), u.getUsername());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long dataId(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createProject(String token) throws Exception {
        ProjectCreateRequest req = new ProjectCreateRequest("P-" + System.nanoTime(), "d");
        MvcResult r = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntityTemplate(long projectId, String token) throws Exception {
        String schema = "{\"fields\":[{\"key\":\"mesh\",\"label\":\"网格\",\"type\":\"NUMBER\"}]}";
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", schema));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntity(long projectId, String token, long templateId, String name) throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(
                        templateId, null, name, null, "{\"mesh\":1}", null, null));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private void addMember(long projectId, long userId, String role) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(role);
        projectMemberMapper.insert(m);
    }

    @Test
    void createEntity_writesOperationLog_withDetailSnapshot() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long et = createEntityTemplate(projectId, token);
        long entityId = createEntity(projectId, token, et, "涡轮方案");

        // Admin（项目创建者）查询日志：应出现 CREATE_ENTITY 记录，targetId=实体 id，detail 含快照
        MvcResult r = mockMvc.perform(get("/api/projects/{pid}/logs", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String json = r.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json).contains("\"action\":\"CREATE_ENTITY\"");
        org.assertj.core.api.Assertions.assertThat(json).contains("\"targetType\":\"ENTITY\"");
        // targetId 为实体雪花 id（Long 全局序列化为 JSON 字符串，防 JS 精度丢失）
        org.assertj.core.api.Assertions.assertThat(json).contains("\"targetId\":\"" + entityId + "\"");
        // detail 快照含实体名
        org.assertj.core.api.Assertions.assertThat(json).contains("涡轮方案");
    }

    @Test
    void listLogs_byViewer_forbidden() throws Exception {
        String adminToken = createUserAndToken();
        long projectId = createProject(adminToken);
        // 非 Admin 成员（Viewer）
        String viewerToken = createUserAndToken();
        long viewerId = userIdHolder[0];
        addMember(projectId, viewerId, "VIEWER");

        mockMvc.perform(get("/api/projects/{pid}/logs", projectId)
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2001));
    }
}
