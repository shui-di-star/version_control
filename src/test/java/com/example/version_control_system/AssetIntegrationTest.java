package com.example.version_control_system;

import com.example.version_control_system.config.MinioProperties;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段八 8.2/8.3：产出物上传/列表/下载/删除集成测试。
 * <p>需 MinIO 的用例（二进制上传/下载/删除）在无本地 MinIO 时用 Assumptions 跳过；
 * TEXT 内联、列表、大小上限、权限等不依赖 MinIO 的用例始终执行。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssetIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired ProjectMemberMapper projectMemberMapper;
    @Autowired JwtService jwtService;
    @Autowired MinioProperties minioProperties;

    private final long[] userIdHolder = new long[1];

    private boolean minioReachable() {
        try {
            URI uri = URI.create(minioProperties.getEndpoint() + "/minio/health/live");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

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
        String schema = "{\"fields\":[{\"key\":\"m\",\"label\":\"m\",\"type\":\"NUMBER\"}]}";
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", schema));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntity(long projectId, String token, long templateId) throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(
                        templateId, null, "n", null, "{\"m\":1}", null, null));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long setup(String token, long[] entityHolder) throws Exception {
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        entityHolder[0] = createEntity(projectId, token, t);
        return projectId;
    }

    @Test
    void uploadText_inline_ok() throws Exception {
        String token = createUserAndToken();
        long[] e = new long[1];
        long projectId = setup(token, e);
        mockMvc.perform(post("/api/projects/{pid}/entities/{eid}/assets/text", projectId, e[0])
                        .header("Authorization", bearer(token))
                        .param("fileName", "note.txt").param("contentText", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetType").value("TEXT"))
                .andExpect(jsonPath("$.data.contentText").value("hello"))
                .andExpect(jsonPath("$.data.objectKey").doesNotExist());
    }

    @Test
    void list_returnsEntityAssets() throws Exception {
        String token = createUserAndToken();
        long[] e = new long[1];
        long projectId = setup(token, e);
        mockMvc.perform(post("/api/projects/{pid}/entities/{eid}/assets/text", projectId, e[0])
                        .header("Authorization", bearer(token))
                        .param("fileName", "a.txt").param("contentText", "x"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/projects/{pid}/entities/{eid}/assets", projectId, e[0])
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void upload_byViewer_forbidden() throws Exception {
        String adminToken = createUserAndToken();
        long[] e = new long[1];
        long projectId = setup(adminToken, e);
        String viewerToken = createUserAndToken();
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userIdHolder[0]);
        m.setRole("VIEWER");
        projectMemberMapper.insert(m);
        mockMvc.perform(post("/api/projects/{pid}/entities/{eid}/assets/text", projectId, e[0])
                        .header("Authorization", bearer(viewerToken))
                        .param("fileName", "a.txt").param("contentText", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void binary_upload_download_delete_roundtrip() throws Exception {
        assumeTrue(minioReachable(), "MinIO 未运行（:9000），跳过需环境的测试");
        String token = createUserAndToken();
        long[] e = new long[1];
        long projectId = setup(token, e);
        byte[] content = "binary-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "doc.bin",
                "application/octet-stream", content);

        MvcResult uploaded = mockMvc.perform(multipart("/api/projects/{pid}/entities/{eid}/assets", projectId, e[0])
                        .file(file).param("assetType", "DOC")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKey").isNotEmpty())
                .andReturn();
        long assetId = dataId(uploaded);

        byte[] downloaded = mockMvc.perform(get("/api/projects/{pid}/assets/{aid}/download", projectId, assetId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(downloaded).isEqualTo(content);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/projects/{pid}/assets/{aid}", projectId, assetId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
