package com.example.version_control_system;

import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.entity.User;
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
 * 阶段九 9.1：关键字搜索集成测试。命中实体 name/remark、关系 remark；无关项不返回；标注来源类型与 id。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
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

    private long createProject(String token) throws Exception {
        ProjectCreateRequest req = new ProjectCreateRequest("P-" + System.nanoTime(), "d");
        MvcResult r = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntityTemplate(long projectId, String token) throws Exception {
        String schema = "{\"fields\":[{\"key\":\"m\",\"label\":\"m\",\"type\":\"NUMBER\"}]}";
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", schema));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntity(long projectId, String token, long templateId, String name, String remark)
            throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(
                        templateId, null, name, remark, "{\"m\":1}", null, null));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createRelation(long projectId, String token, long templateId, long from, long to, String remark)
            throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.RelationCreateRequest(templateId, from, to, remark));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/relations", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createRelationTemplate(long projectId, String token) throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.RelationTemplateRequest("rt", 1, null, null, null));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/relation-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    @Test
    void search_matchesEntityAndRelation() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long hitByName = createEntity(projectId, token, t, "涡轮ZEBRA方案", "普通备注");
        long hitByRemark = createEntity(projectId, token, t, "方案二", "含ZEBRA关键词");
        createEntity(projectId, token, t, "无关方案", "无关备注");

        long rt = createRelationTemplate(projectId, token);
        long relHit = createRelation(projectId, token, rt, hitByName, hitByRemark, "关系ZEBRA备注");

        MvcResult r = mockMvc.perform(get("/api/projects/{pid}/search", projectId)
                        .header("Authorization", bearer(token)).param("keyword", "ZEBRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn();

        String json = r.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json).contains("\"sourceType\":\"ENTITY\"");
        org.assertj.core.api.Assertions.assertThat(json).contains("\"sourceType\":\"RELATION\"");
        org.assertj.core.api.Assertions.assertThat(json).contains(String.valueOf(relHit));
        // 无关项不出现
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain("无关");
    }

    @Test
    void search_noMatch_empty() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        createEntity(projectId, token, t, "方案", "备注");
        mockMvc.perform(get("/api/projects/{pid}/search", projectId)
                        .header("Authorization", bearer(token)).param("keyword", "NOPE_NOTHING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
