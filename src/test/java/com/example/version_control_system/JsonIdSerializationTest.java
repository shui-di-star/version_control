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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 校验全局 Jackson 定制：雪花 id（Long 包装类型）在 JSON 中序列化为字符串（防 JS 精度丢失），
 * 而统计计数（基本型 long）仍为数字。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JsonIdSerializationTest {

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

    @Test
    void projectId_serializedAsJsonString() throws Exception {
        String token = createUserAndToken();
        ProjectCreateRequest req = new ProjectCreateRequest("P-" + System.nanoTime(), "d");
        MvcResult r = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        String json = r.getResponse().getContentAsString();

        // id 必须是带引号的字符串形式："id":"2072..."，而非裸数字 "id":2072...
        assertThat(json).containsPattern("\"id\":\"\\d{15,}\"");
        assertThat(json).doesNotContainPattern("\"id\":\\d");

        // 读取端仍可按 long 解析（Jackson 从字符串解析数值）
        long id = objectMapper.readTree(json).path("data").path("id").asLong();
        assertThat(id).isPositive();
    }

    @Test
    void statsCounts_remainJsonNumbers() throws Exception {
        String token = createUserAndToken();
        ProjectCreateRequest req = new ProjectCreateRequest("P-" + System.nanoTime(), "d");
        MvcResult pr = mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(pr.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult sr = mockMvc.perform(get("/api/projects/{pid}/stats", projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        String json = sr.getResponse().getContentAsString();
        // 基本型 long 计数不受 Long→String 定制影响，仍为数字（"totalNodes":0）
        assertThat(json).containsPattern("\"totalNodes\":\\d");
        assertThat(json).doesNotContainPattern("\"totalNodes\":\"");
    }
}
