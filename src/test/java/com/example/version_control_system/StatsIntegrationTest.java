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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段七 7.2：统计面板集成测试。构造已知数据集断言各项数值。
 * 已完成仿真 = RECOMMENDED + DEPRECATED + COMPLETED；指定 NUMBER 字段最大值。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsIntegrationTest {

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
        String schema = "{\"fields\":[{\"key\":\"cpu\",\"label\":\"核数\",\"type\":\"NUMBER\"}]}";
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", schema));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private long createEntity(long projectId, String token, long templateId, String name, double cpu)
            throws Exception {
        var body = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityCreateRequest(
                        templateId, null, name, null, "{\"cpu\":" + cpu + "}", null, null));
        MvcResult r = mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return dataId(r);
    }

    private void setStatus(long projectId, String token, long id, String status) throws Exception {
        mockMvc.perform(put("/api/projects/{pid}/entities/{id}/status", projectId, id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void stats_knownDataset() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        long e1 = createEntity(projectId, token, t, "n1", 4);   // RECOMMENDED
        long e2 = createEntity(projectId, token, t, "n2", 16);  // DEPRECATED
        long e3 = createEntity(projectId, token, t, "n3", 8);   // COMPLETED
        long e4 = createEntity(projectId, token, t, "n4", 32);  // SIMULATING
        createEntity(projectId, token, t, "n5", 2);             // 无状态
        setStatus(projectId, token, e1, "RECOMMENDED");
        setStatus(projectId, token, e2, "DEPRECATED");
        setStatus(projectId, token, e3, "COMPLETED");
        setStatus(projectId, token, e4, "SIMULATING");

        // total=5, completedSim=RECOMMENDED+DEPRECATED+COMPLETED=3, simulating=1, recommended=1, maxCpu=32
        mockMvc.perform(get("/api/projects/{pid}/stats", projectId)
                        .header("Authorization", bearer(token))
                        .param("numberFieldKey", "cpu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalNodes").value(5))
                .andExpect(jsonPath("$.data.completedSim").value(3))
                .andExpect(jsonPath("$.data.simulating").value(1))
                .andExpect(jsonPath("$.data.recommended").value(1))
                .andExpect(jsonPath("$.data.maxNumberValue").value(32.0));
    }

    @Test
    void stats_noNumberKey_maxNull() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        long t = createEntityTemplate(projectId, token);
        createEntity(projectId, token, t, "n1", 4);
        mockMvc.perform(get("/api/projects/{pid}/stats", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalNodes").value(1))
                .andExpect(jsonPath("$.data.maxNumberValue").doesNotExist());
    }

    /**
     * 非 ASCII 字段 key（中文）求最大值：JSON path 未加引号时 MySQL 报
     * "Invalid JSON path expression" → 500。验证 path 加双引号后中文 key 正常。
     */
    @Test
    void stats_chineseNumberKey_ok() throws Exception {
        String token = createUserAndToken();
        long projectId = createProject(token);
        // NUMBER 字段 key 用中文「网格尺寸」
        String schema = "{\"fields\":[{\"key\":\"网格尺寸\",\"label\":\"网格\",\"type\":\"NUMBER\"}]}";
        var tplBody = objectMapper.writeValueAsString(
                new com.example.version_control_system.dto.EntityTemplateRequest("模板", schema));
        MvcResult tr = mockMvc.perform(post("/api/projects/{pid}/entity-templates", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(tplBody))
                .andExpect(status().isOk()).andReturn();
        long t = dataId(tr);
        // 两个实体，「网格尺寸」分别 2.5 / 5.0
        for (double v : new double[]{2.5, 5.0}) {
            var eBody = objectMapper.writeValueAsString(
                    new com.example.version_control_system.dto.EntityCreateRequest(
                            t, null, "n_" + v, null, "{\"网格尺寸\":" + v + "}", null, null));
            mockMvc.perform(post("/api/projects/{pid}/entities", projectId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(eBody))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/projects/{pid}/stats", projectId)
                        .header("Authorization", bearer(token))
                        .param("numberFieldKey", "网格尺寸"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxNumberValue").value(5.0));
    }
}
