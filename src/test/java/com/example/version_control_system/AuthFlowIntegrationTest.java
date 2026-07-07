package com.example.version_control_system;

import com.example.version_control_system.dto.LoginRequest;
import com.example.version_control_system.dto.RegisterRequest;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.mapper.UserMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段三端到端认证测试（3.1/3.2/3.3/3.4）。连 vcs_test，@Transactional 回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;

    private String uniqueName() {
        return "user_" + System.nanoTime();
    }

    // ===== 3.1 注册 =====
    @Test
    void register_success_storesBcryptHash() throws Exception {
        String username = uniqueName();
        RegisterRequest req = new RegisterRequest(username, "secret123", "a@b.com", "Alice");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.systemRole").value("USER"));

        User stored = userMapper.selectList(null).stream()
                .filter(u -> username.equals(u.getUsername())).findFirst().orElseThrow();
        // 存的是 BCrypt 哈希而非明文
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret123").startsWith("$2");
    }

    @Test
    void register_duplicateUsername_conflict() throws Exception {
        String username = uniqueName();
        RegisterRequest req = new RegisterRequest(username, "secret123", null, null);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
        // 重名再注册 → 冲突码 3001
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(3001));
    }

    // ===== 3.2 登录 =====
    @Test
    void login_success_returnsJwtWithClaims() throws Exception {
        String username = register(uniqueName(), "secret123");
        String token = login(username, "secret123");
        assertThat(token).isNotBlank();
        // JWT 三段
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void login_wrongPassword_unauthorized() throws Exception {
        String username = register(uniqueName(), "secret123");
        LoginRequest req = new LoginRequest(username, "wrongpass");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(2000));
    }

    // ===== 3.3 过滤器 + 3.4 me =====
    @Test
    void me_withValidToken_returnsUser() throws Exception {
        String username = register(uniqueName(), "secret123");
        String token = login(username, "secret123");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void protectedEndpoint_noToken_401() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_forgedToken_401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpoint_noToken_accessible() throws Exception {
        // 登录端点公开：无 token 也能到达（这里传错密码，得到业务错误而非 401）
        LoginRequest req = new LoginRequest("nobody", "x");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000));
    }

    // ===== 3.4 登出：登出后旧 token 失效 =====
    @Test
    void logout_invalidatesPreviouslyIssuedToken() throws Exception {
        String username = register(uniqueName(), "secret123");
        String token = login(username, "secret123");
        // 登出前可访问
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // 登出
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // 登出后旧 token 被拒（iat 早于 token_invalid_before）
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----
    private String register(String username, String password) throws Exception {
        RegisterRequest req = new RegisterRequest(username, password, null, null);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
        return username;
    }

    private String login(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("token").asText();
    }
}
