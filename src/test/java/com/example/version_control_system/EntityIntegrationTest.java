package com.example.version_control_system;

import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.entity.User;
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
 * 阶段六：实体 CRUD（6.1/6.2）、整树查询（6.3）、路径追溯（6.4）、里程碑与状态（6.5）集成测试。
 * 连 vcs_test，@Transactional 回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired SimEntityMapper simEntityMapper;
    @Autowired com.example.version_control_system.mapper.ProjectMemberMapper projectMemberMapper;
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
        MvcResult r = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    /** 建实体模板，含一个必填 NUMBER 字段 mesh_size 与一个 ENUM 字段 solver。返回模板 id。 */
    private long createEntityTemplate(long projectId, String token) throws Exception {
        String schema = "{\"fields\":["
                + "{\"key\":\"mesh_size\",\"label\":\"网格\",\"type\":\"NUMBER\",\"required\":true},"
                + "{\"key\":\"solver\",\"label\":\"求解器\",\"type\":\"ENUM\",\"options\":[\"Abaqus\",\"Ansys\"]}"
                + "]}";
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", schema));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    /** 获取项目中第一个关系模板 id（项目创建时自动带预设关系模板）。 */
    private long firstRelationTemplateId(long projectId, String token) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/projects/{pid}/relation-templates", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
    }

    /** 创建实体，返回响应（调用方自行断言）。 */
    private MvcResult createEntity(long projectId, String token, long templateId,
                                   Long parentId, String name, String attributes) throws Exception {
        Long relTplId = parentId != null ? firstRelationTemplateId(projectId, token) : null;
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(
                        templateId, parentId, name, null, attributes, relTplId, null));
        return mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    /** 创建实体并断言成功，返回新 id。 */
    private long createEntityOk(long projectId, String token, long templateId,
                                Long parentId, String name) throws Exception {
        MvcResult r = createEntity(projectId, token, templateId, parentId, name,
                "{\"mesh_size\":2.5,\"solver\":\"Abaqus\"}");
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(r.getResponse().getContentAsString()).path("code").asInt()).isEqualTo(0);
        return dataId(r);
    }

    private long dataId(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private JsonNode data(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
    }

    // ===== 6.1 创建与属性校验 =====

    @Test
    void create_valid_ok() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long templateId = createEntityTemplate(projectId, token);
        MvcResult r = createEntity(projectId, token, templateId, null, "根节点",
                "{\"mesh_size\":2.5,\"solver\":\"Abaqus\"}");
        org.assertj.core.api.Assertions.assertThat(data(r).path("name").asString()).isEqualTo("根节点");
        org.assertj.core.api.Assertions.assertThat(data(r).path("attributes").asString()).contains("mesh_size");
    }

    @Test
    void create_missingRequired_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long templateId = createEntityTemplate(projectId, token);
        // 缺必填 mesh_size → BAD_REQUEST 1000
        MvcResult r = createEntity(projectId, token, templateId, null, "缺字段", "{\"solver\":\"Abaqus\"}");
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(r.getResponse().getContentAsString()).path("code").asInt()).isEqualTo(1000);
    }

    @Test
    void create_typeMismatch_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long templateId = createEntityTemplate(projectId, token);
        // mesh_size 传字符串（应为数字）→ 1000
        MvcResult r = createEntity(projectId, token, templateId, null, "类型错",
                "{\"mesh_size\":\"big\",\"solver\":\"Abaqus\"}");
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(r.getResponse().getContentAsString()).path("code").asInt()).isEqualTo(1000);
    }

    @Test
    void create_enumNotInOptions_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long templateId = createEntityTemplate(projectId, token);
        MvcResult r = createEntity(projectId, token, templateId, null, "枚举错",
                "{\"mesh_size\":1,\"solver\":\"Nastran\"}");
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(r.getResponse().getContentAsString()).path("code").asInt()).isEqualTo(1000);
    }

    @Test
    void create_parentInOtherProject_rejected() throws Exception {
        String token = createUserAndToken("USER");
        long projectA = createProject(token);
        long templateA = createEntityTemplate(projectA, token);
        long rootA = createEntityOk(projectA, token, templateA, null, "A根");
        // 另一项目 B，父节点指向 A 的实体 → Service 校验拒绝（父不在本项目→NOT_FOUND 3000）
        long projectB = createProject(token);
        long templateB = createEntityTemplate(projectB, token);
        MvcResult r = createEntity(projectB, token, templateB, rootA, "越界子",
                "{\"mesh_size\":1,\"solver\":\"Abaqus\"}");
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(r.getResponse().getContentAsString()).path("code").asInt()).isEqualTo(3000);
    }

    // ===== 6.2 更新与删除策略 =====

    @Test
    void update_nameAndAttributes_ok() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long templateId = createEntityTemplate(projectId, token);
        long id = createEntityOk(projectId, token, templateId, null, "旧名");
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityUpdateRequest(
                        "新名", "备注", "{\"mesh_size\":9,\"solver\":\"Ansys\"}"));
        mockMvc.perform(put("/api/projects/{pid}/entities/{id}", projectId, id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名"))
                .andExpect(jsonPath("$.data.remark").value("备注"));
    }

    @Test
    void delete_cascade_removesSubtree() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long root = createEntityOk(projectId, token, t, null, "root");
        long child = createEntityOk(projectId, token, t, root, "child");
        long grand = createEntityOk(projectId, token, t, child, "grand");
        // CASCADE 删 root → 整棵子树消失
        mockMvc.perform(delete("/api/projects/{pid}/entities/{id}", projectId, root)
                        .header("Authorization", bearer(token)).param("childStrategy", "CASCADE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(root)).isNull();
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(child)).isNull();
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(grand)).isNull();
    }

    @Test
    void delete_promote_liftsChildren() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long root = createEntityOk(projectId, token, t, null, "root");
        long child = createEntityOk(projectId, token, t, root, "child");
        long grand = createEntityOk(projectId, token, t, child, "grand");
        // PROMOTE 删 child → child 消失，grand 的 parent 上提到 root
        mockMvc.perform(delete("/api/projects/{pid}/entities/{id}", projectId, child)
                        .header("Authorization", bearer(token)).param("childStrategy", "PROMOTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(child)).isNull();
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(grand).getParentId()).isEqualTo(root);
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(root)).isNotNull();
    }

    @Test
    void delete_promoteRoot_childBecomesRoot() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long root = createEntityOk(projectId, token, t, null, "root");
        long child = createEntityOk(projectId, token, t, root, "child");
        // PROMOTE 删根 → child 变根（parent_id=NULL）
        mockMvc.perform(delete("/api/projects/{pid}/entities/{id}", projectId, root)
                        .header("Authorization", bearer(token)).param("childStrategy", "PROMOTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(root)).isNull();
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(child).getParentId()).isNull();
    }

    // ===== 6.3 整树查询 =====

    @Test
    void tree_nestedShape() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long root = createEntityOk(projectId, token, t, null, "root");
        long child = createEntityOk(projectId, token, t, root, "child");
        createEntityOk(projectId, token, t, child, "grand");
        // 一个根，root 下一个 child，child 下一个 grand
        mockMvc.perform(get("/api/projects/{pid}/entities/tree", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(root))
                .andExpect(jsonPath("$.data[0].children.length()").value(1))
                .andExpect(jsonPath("$.data[0].children[0].id").value(child))
                .andExpect(jsonPath("$.data[0].children[0].children[0].name").value("grand"));
    }

    // ===== 6.4 路径追溯 =====

    @Test
    void path_rootToNode_ordered() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long root = createEntityOk(projectId, token, t, null, "root");
        long child = createEntityOk(projectId, token, t, root, "child");
        long grand = createEntityOk(projectId, token, t, child, "grand");
        // grand 的路径：根 → grand，首为 root，末为 grand
        mockMvc.perform(get("/api/projects/{pid}/entities/{id}/path", projectId, grand)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(root))
                .andExpect(jsonPath("$.data[2].id").value(grand));
    }

    // ===== 6.5 里程碑与状态 =====

    @Test
    void milestone_toggle() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long id = createEntityOk(projectId, token, t, null, "n");
        // 默认 0 → 切换为 1
        mockMvc.perform(put("/api/projects/{pid}/entities/{id}/milestone", projectId, id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMilestone").value(1));
        // 再切回 0
        mockMvc.perform(put("/api/projects/{pid}/entities/{id}/milestone", projectId, id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMilestone").value(0));
    }

    @Test
    void status_setClearInvalid() throws Exception {
        String token = createUserAndToken("USER");
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long id = createEntityOk(projectId, token, t, null, "n");
        // 设 COMPLETED
        mockMvc.perform(put("/api/projects/{pid}/entities/{id}/status", projectId, id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        // 清空
        mockMvc.perform(put("/api/projects/{pid}/entities/{id}/status", projectId, id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":null}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(simEntityMapper.selectById(id).getStatus()).isNull();
        // 非法值 → 1000
        mockMvc.perform(put("/api/projects/{pid}/entities/{id}/status", projectId, id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BOGUS\"}"))
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void write_byViewer_forbidden() throws Exception {
        String adminToken = createUserAndToken("USER");
        long projectId = createProject(adminToken);
        long t = createEntityTemplate(projectId, adminToken);
        // Viewer 成员创建实体 → 403（写需 Editor+）
        String viewerToken = createUserAndToken("USER");
        addViewer(projectId, userIdHolder[0]);
        MvcResult r = createEntity(projectId, viewerToken, t, null, "x",
                "{\"mesh_size\":1,\"solver\":\"Abaqus\"}");
        org.assertj.core.api.Assertions.assertThat(r.getResponse().getStatus()).isEqualTo(403);
    }

    private void addViewer(long projectId, long userId) {
        com.example.version_control_system.entity.ProjectMember m =
                new com.example.version_control_system.entity.ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole("VIEWER");
        projectMemberMapper.insert(m);
    }
}
