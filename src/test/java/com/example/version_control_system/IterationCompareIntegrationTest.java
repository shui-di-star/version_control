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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段十 10.4：迭代对比与路径追溯收尾。确认对比所需数据接口齐备——
 * 给定若干节点 id，可复用实体详情（属性）、产出物列表（元信息）、路径追溯接口取回全部对比数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IterationCompareIntegrationTest {

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
        String schema = "{\"fields\":[{\"key\":\"cpu\",\"label\":\"CPU\",\"type\":\"NUMBER\"}]}";
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", null, schema));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntity(long projectId, String token, long templateId, Long parentId,
                              String name, String attrs) throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(
                        templateId, parentId, name, null, attrs, null, null));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private void uploadText(long projectId, long entityId, String token, String fileName, String content)
            throws Exception {
        mockMvc.perform(post("/api/projects/{pid}/entities/{eid}/assets/text", projectId, entityId)
                        .header("Authorization", bearer(token))
                        .param("fileName", fileName).param("contentText", content))
                .andExpect(status().isOk());
    }

    @Test
    void compareTwoNodes_attributesPathAndAssets_allRetrievable() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long et = createEntityTemplate(projectId, token);
        long root = createEntity(projectId, token, et, null, "v1", "{\"cpu\":8}");
        long child = createEntity(projectId, token, et, root, "v2", "{\"cpu\":16}");
        uploadText(projectId, child, token, "notes.txt", "迭代二说明");

        // 1) 逐节点取属性（对比数据来源）
        MvcResult r1 = mockMvc.perform(get("/api/projects/{pid}/entities/{eid}", projectId, root)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        MvcResult r2 = mockMvc.perform(get("/api/projects/{pid}/entities/{eid}", projectId, child)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode a1 = objectMapper.readTree(r1.getResponse().getContentAsString()).path("data");
        JsonNode a2 = objectMapper.readTree(r2.getResponse().getContentAsString()).path("data");
        assertThat(a1.path("attributes").asText()).contains("\"cpu\": 8");
        assertThat(a2.path("attributes").asText()).contains("\"cpu\": 16");

        // 2) 路径追溯：child 的路径为 root→child（根到当前）
        MvcResult rp = mockMvc.perform(get("/api/projects/{pid}/entities/{eid}/path", projectId, child)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode path = objectMapper.readTree(rp.getResponse().getContentAsString()).path("data");
        assertThat(path.size()).isEqualTo(2);
        assertThat(path.get(0).path("id").asLong()).isEqualTo(root);
        assertThat(path.get(1).path("id").asLong()).isEqualTo(child);

        // 3) 产出物元信息：child 有一条 TEXT 产出物
        MvcResult ra = mockMvc.perform(get("/api/projects/{pid}/entities/{eid}/assets", projectId, child)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode assets = objectMapper.readTree(ra.getResponse().getContentAsString()).path("data");
        assertThat(assets.size()).isEqualTo(1);
        assertThat(assets.get(0).path("fileName").asText()).isEqualTo("notes.txt");
    }
}
