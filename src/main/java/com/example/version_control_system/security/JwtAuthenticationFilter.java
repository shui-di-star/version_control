package com.example.version_control_system.security;

import com.example.version_control_system.entity.User;
import com.example.version_control_system.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * JWT 认证过滤器：解析 {@code Authorization: Bearer <token>}，验证通过后填充 SecurityContext。
 * <p>额外校验：token 的签发时间 iat 若早于该用户 {@code token_invalid_before}，视为已登出/失效（决策 6）。</p>
 * <p>解析失败不抛异常、不填充上下文——放行给后续的授权决策（未认证访问受保护资源最终由 EntryPoint 返回 401）。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        // 支持 query param 方式传递 token（用于 img src 等无法设 header 的场景）
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                header = BEARER_PREFIX + queryToken;
            }
        }
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                Long userId = jwtService.getUserId(claims);
                User user = userMapper.selectById(userId);
                if (user != null && isTokenStillValid(user, jwtService.getIssuedAt(claims))) {
                    AuthUser principal = new AuthUser(user.getId(), user.getUsername(), user.getSystemRole());
                    var authToken = new UsernamePasswordAuthenticationToken(
                            principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getSystemRole())));
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception ex) {
                // 非法/过期/伪造 token：不填充上下文，交由授权层拒绝
                log.debug("JWT 校验失败: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * iat 不早于 token_invalid_before 才有效（决策 6）。
     * <p>JWT 的 iat 被截断到整秒（毫秒位恒为 000），而 {@code token_invalid_before} 登出时带真实毫秒，
     * 因此直接比较原始毫秒即可可靠判定：登出后签发前的 token（iat 秒 &lt; 登出毫秒）必被判失效，
     * 无需担心"同一秒签发又登出"的边界误判。</p>
     */
    private boolean isTokenStillValid(User user, Date issuedAt) {
        LocalDateTime invalidBefore = user.getTokenInvalidBefore();
        if (invalidBefore == null) {
            return true;
        }
        Date invalidBeforeDate = Date.from(invalidBefore.atZone(ZoneId.systemDefault()).toInstant());
        return issuedAt.getTime() >= invalidBeforeDate.getTime();
    }
}
