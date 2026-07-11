package com.example.version_control_system;

import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段五：实体模板 CRUD（5.1）、关系模板 CRUD（5.2）集成测试。
 * 连 vcs_test，@Transactional 回滚。覆盖：Admin 增删改、成员查、field_schema 非法被拒、
 * 被引用模板删除被拒、角色不足写操作被拒。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TemplateIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired ProjectMemberMapper projectMemberMapper;
    @Autowired SimEntityMapper simEntityMapper;
    @Autowired RelationMapper relationMapper;
    @Autowired JwtService jwtService;

    private final long[] userIdHolder = new long[1];

    private String createUserAndToken(String systemRole) {
        User u = new User();
        u.setUsername("u_" + System.nanoTime());
        u.setPasswordHash("$2a$x");
        u.setSystemRole(systemRole);
        u.setStatus(1);
        userMapper.insert(u);
        userIdHolder[0] = u.getId();
        return jwtService.generateToken(u.getId(), u.getUsername());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long createProject(String token) throws Exception {
        ProjectCreateRequest req = new ProjectCreateRequest("P-" + System.nanoTime(), "d");
        MvcResult result = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void addMember(long projectId, long userId, String role) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(role);
        projectMemberMapper.insert(m);
    }

    private long readDataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String entityTemplateBody(String name, String fieldSchema) throws Exception {
        return objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest(name, fieldSchema));
    }

    private String relationTemplateBody(String name, Integer directed, String lineStyle,
                                        String allowedFrom, String allowedTo) throws Exception {
        return objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.RelationTemplateRequest(
                        name, directed, lineStyle, allowedFrom, allowedTo));
    }

    // ===== 5.1 实体模板 CRUD =====

    private static final String VALID_SCHEMA =
            "{\"fields\":[{\"key\":\"mesh_size\",\"label\":\"网格尺寸\",\"type\":\"NUMBER\"}]}";

    @Test
    void entityTemplate_adminCrud_ok() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);

        // create
        MvcResult created = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(entityTemplateBody("网格模板", VALID_SCHEMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("网格模板"))
                .andReturn();
        long templateId = readDataId(created);

        // get
        mockMvc.perform(get("/api/projects/{pid}/entity-templates/{tid}", projectId, templateId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(templateId));

        // update
        mockMvc.perform(put("/api/projects/{pid}/entity-templates/{tid}", projectId, templateId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(entityTemplateBody("改名模板", VALID_SCHEMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名模板"));

        // list
        mockMvc.perform(get("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // delete
        mockMvc.perform(delete("/api/projects/{pid}/entity-templates/{tid}", projectId, templateId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void entityTemplate_invalidFieldSchema_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        // ENUM 缺 options → BAD_REQUEST 1000
        String badSchema = "{\"fields\":[{\"key\":\"solver\",\"label\":\"求解器\",\"type\":\"ENUM\"}]}";
        mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(entityTemplateBody("坏模板", badSchema)))
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void entityTemplate_deleteWhenReferenced_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        MvcResult created = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(entityTemplateBody("被引用模板", VALID_SCHEMA)))
                .andExpect(status().isOk()).andReturn();
        long templateId = readDataId(created);

        // 直接插一条引用该模板的实体
        SimEntity e = new SimEntity();
        e.setProjectId(projectId);
        e.setTemplateId(templateId);
        e.setName("node");
        e.setIsMilestone(0);
        simEntityMapper.insert(e);

        // 被引用 → 删除拒绝 CONFLICT 3001
        mockMvc.perform(delete("/api/projects/{pid}/entity-templates/{tid}", projectId, templateId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    void entityTemplate_writeByEditor_forbidden() throws Exception {
        String adminToken = createUserAndToken("USER");
        long projectId = createProject(adminToken);
        String editorToken = createUserAndToken("USER");
        addMember(projectId, userIdHolder[0], "EDITOR");
        // Editor 写模板 → 403（模板管理需 Admin）
        mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(editorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(entityTemplateBody("模板", VALID_SCHEMA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void entityTemplate_readByViewer_ok() throws Exception {
        String adminToken = createUserAndToken("USER");
        long projectId = createProject(adminToken);
        String viewerToken = createUserAndToken("USER");
        addMember(projectId, userIdHolder[0], "VIEWER");
        mockMvc.perform(get("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ===== 5.2 关系模板 CRUD =====

    @Test
    void relationTemplate_adminCrud_ok() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);

        MvcResult created = mockMvc.perform(post("/api/projects/{pid}/relation-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relationTemplateBody("参考自", 1, "{\"color\":\"#f00\"}", "[1,2]", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("参考自"))
                .andExpect(jsonPath("$.data.directed").value(1))
                .andReturn();
        long templateId = readDataId(created);

        mockMvc.perform(put("/api/projects/{pid}/relation-templates/{tid}", projectId, templateId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relationTemplateBody("派生自", 0, null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.directed").value(0));

        mockMvc.perform(delete("/api/projects/{pid}/relation-templates/{tid}", projectId, templateId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void relationTemplate_invalidLineStyle_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        // lineStyle 是数组而非对象 → BAD_REQUEST 1000
        mockMvc.perform(post("/api/projects/{pid}/relation-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relationTemplateBody("坏关系", 1, "[1,2]", null, null)))
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void relationTemplate_deleteWhenReferenced_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        MvcResult created = mockMvc.perform(post("/api/projects/{pid}/relation-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relationTemplateBody("被引用关系", 1, null, null, null)))
                .andExpect(status().isOk()).andReturn();
        long templateId = readDataId(created);

        Relation r = new Relation();
        r.setProjectId(projectId);
        r.setTemplateId(templateId);
        r.setFromEntityId(1L);
        r.setToEntityId(2L);
        relationMapper.insert(r);

        mockMvc.perform(delete("/api/projects/{pid}/relation-templates/{tid}", projectId, templateId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(3001));
    }
}
