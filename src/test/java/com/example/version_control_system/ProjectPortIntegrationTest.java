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
 * 阶段十 10.2/10.3：项目导出 → 导入到另一空项目集成测试。
 * 断言：导出 JSON 含全量数据；导入后目标项目树结构与引用完整还原、所有 id 均为新分配、无冲突。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectPortIntegrationTest {

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
        String schema = "{\"fields\":[{\"key\":\"mesh\",\"label\":\"网格\",\"type\":\"NUMBER\"}]}";
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", null, schema));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntity(long projectId, String token, long templateId, Long parentId, String name)
            throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(
                        templateId, parentId, name, null, "{\"mesh\":1}", null, null));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
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

    private long createRelation(long projectId, String token, long templateId, long from, long to)
            throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.RelationCreateRequest(templateId, from, to, "r"));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/relations", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    @Test
    void export_thenImport_intoEmptyProject_reconstructsTreeAndRefs() throws Exception {
        String token = createUserAndToken();
        long src = createProject(token);
        long et = createEntityTemplate(src, token);
        long root = createEntity(src, token, et, null, "root");
        long child = createEntity(src, token, et, root, "child");
        long rt = createRelationTemplate(src, token);
        createRelation(src, token, rt, root, child);

        // 导出
        MvcResult exportRes = mockMvc.perform(get("/api/projects/{pid}/export", src)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode exportData = objectMapper.readTree(exportRes.getResponse().getContentAsString()).path("data");
        // 导出含全量数据
        assertThat(exportData.path("entityTemplates").size()).isEqualTo(1);
        assertThat(exportData.path("relationTemplates").size()).isEqualTo(1);
        assertThat(exportData.path("entities").size()).isEqualTo(2);
        assertThat(exportData.path("relations").size()).isEqualTo(1);

        // 导入到另一个空项目
        long dst = createProject(token);
        mockMvc.perform(post("/api/projects/{pid}/import", dst)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exportData.toString()))
                .andExpect(status().isOk());

        // 目标项目树：2 节点，root 无父，child 父指向 root（新 id）
        MvcResult treeRes = mockMvc.perform(get("/api/projects/{pid}/entities/tree", dst)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode tree = objectMapper.readTree(treeRes.getResponse().getContentAsString()).path("data");
        assertThat(tree.size()).isEqualTo(1);                       // 一个根
        JsonNode newRoot = tree.get(0);
        assertThat(newRoot.path("name").asText()).isEqualTo("root");
        assertThat(newRoot.path("children").size()).isEqualTo(1);
        JsonNode newChild = newRoot.path("children").get(0);
        assertThat(newChild.path("name").asText()).isEqualTo("child");

        // 所有 id 均为新分配（与源项目不同）
        long newRootId = newRoot.path("id").asLong();
        long newChildId = newChild.path("id").asLong();
        assertThat(newRootId).isNotEqualTo(root);
        assertThat(newChildId).isNotEqualTo(child);

        // 关系还原：from/to 指向新实体 id
        MvcResult relRes = mockMvc.perform(get("/api/projects/{pid}/relations", dst)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode rels = objectMapper.readTree(relRes.getResponse().getContentAsString()).path("data");
        assertThat(rels.size()).isEqualTo(1);
        assertThat(rels.get(0).path("fromEntityId").asLong()).isEqualTo(newRootId);
        assertThat(rels.get(0).path("toEntityId").asLong()).isEqualTo(newChildId);
    }
}
