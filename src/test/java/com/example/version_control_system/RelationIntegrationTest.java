package com.example.version_control_system;

import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.mapper.RelationMapper;
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
 * 阶段七 7.1：语义关系 CRUD 集成测试。连 vcs_test，@Transactional 回滚。
 * 覆盖：合法创建、allowed_from/allowed_to 约束拒绝、跨项目端点拒绝、
 * 确认父子关系不入 t_relation（本表只有显式语义关系）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired RelationMapper relationMapper;
    @Autowired JwtService jwtService;

    private String createUserAndToken() {
        User u = new User();
        u.setUsername("u_" + System.nanoTime());
        u.setPasswordHash("$2a$x");
        u.setSystemRole("USER");
        u.setStatus(1);
        userMapper.insert(u);
        return jwtService.generateToken(u.getId(), u.getUsername());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long dataId(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private int code(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("code").asInt();
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
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", null, schema));
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

    private long createRelationTemplate(long projectId, String token, String allowedFrom, String allowedTo)
            throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.RelationTemplateRequest(
                        "参考自", 1, null, allowedFrom, allowedTo));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/relation-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private MvcResult createRelation(long projectId, String token, long templateId,
                                     long fromId, long toId) throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.RelationCreateRequest(templateId, fromId, toId, "r"));
        return mockMvc.perform(post("/api/projects/{pid}/relations", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    @Test
    void create_valid_ok() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long et = createEntityTemplate(projectId, token);
        long a = createEntity(projectId, token, et, "A");
        long b = createEntity(projectId, token, et, "B");
        long rt = createRelationTemplate(projectId, token, null, null);
        MvcResult r = createRelation(projectId, token, rt, a, b);
        org.assertj.core.api.Assertions.assertThat(code(r)).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(r.getResponse().getContentAsString())
                        .path("data").path("fromEntityId").asLong()).isEqualTo(a);
    }

    @Test
    void create_allowedFromViolation_rejected() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long et = createEntityTemplate(projectId, token);
        long a = createEntity(projectId, token, et, "A");
        long b = createEntity(projectId, token, et, "B");
        // allowedFrom 限定一个不存在的类型 id 999 → A 的类型不被允许
        long rt = createRelationTemplate(projectId, token, "[999]", null);
        MvcResult r = createRelation(projectId, token, rt, a, b);
        org.assertj.core.api.Assertions.assertThat(code(r)).isEqualTo(1000);
    }

    @Test
    void create_allowedFromMatch_ok() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long et = createEntityTemplate(projectId, token);
        long a = createEntity(projectId, token, et, "A");
        long b = createEntity(projectId, token, et, "B");
        // allowedFrom/allowedTo 都含实体的模板 id et → 放行
        long rt = createRelationTemplate(projectId, token, "[" + et + "]", "[" + et + "]");
        MvcResult r = createRelation(projectId, token, rt, a, b);
        org.assertj.core.api.Assertions.assertThat(code(r)).isEqualTo(0);
    }

    @Test
    void create_endpointInOtherProject_rejected() throws Exception {
        String token = createUserAndToken();
        long projectA = createProject(token);
        long etA = createEntityTemplate(projectA, token);
        long a = createEntity(projectA, token, etA, "A");

        long projectB = createProject(token);
        long etB = createEntityTemplate(projectB, token);
        long b = createEntity(projectB, token, etB, "B");
        long rtB = createRelationTemplate(projectB, token, null, null);
        // 在项目 B 建关系，from 指向项目 A 的实体 → NOT_FOUND 3000
        MvcResult r = createRelation(projectB, token, rtB, a, b);
        org.assertj.core.api.Assertions.assertThat(code(r)).isEqualTo(3000);
    }

    @Test
    void list_and_get_ok() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long et = createEntityTemplate(projectId, token);
        long a = createEntity(projectId, token, et, "A");
        long b = createEntity(projectId, token, et, "B");
        long rt = createRelationTemplate(projectId, token, null, null);
        long relId = dataId(createRelation(projectId, token, rt, a, b));

        mockMvc.perform(get("/api/projects/{pid}/relations", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(relId));

        mockMvc.perform(get("/api/projects/{pid}/relations/{rid}", projectId, relId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(relId));
    }

    @Test
    void parentChild_notWrittenToRelationTable() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long et = createEntityTemplate(projectId, token);
        long root = createEntity(projectId, token, et, "root");
        // 建带父子的实体树：child 的 parent=root（走 t_entity.parent_id，不入 t_relation）
        var childBody = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(et, root, "child", null, "{\"mesh\":1}", null, null));
        mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(childBody))
                .andExpect(status().isOk());
        // t_relation 无任何记录
        long count = relationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.version_control_system.entity.Relation>()
                        .eq(com.example.version_control_system.entity.Relation::getProjectId, projectId));
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }
}
