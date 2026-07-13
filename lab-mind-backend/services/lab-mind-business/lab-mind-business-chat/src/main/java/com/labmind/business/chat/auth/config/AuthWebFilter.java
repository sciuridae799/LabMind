package com.labmind.business.chat.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.auth.AuthErrorCode;
import com.labmind.business.chat.auth.AuthException;
import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.AuthSessionHolder;
import com.labmind.business.chat.auth.service.AuthService;
import com.labmind.common.frame.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AuthWebFilter extends OncePerRequestFilter {

    private final AuthService authService;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isPublicRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AuthSessionContext session = authService.loadSession(extractBearerToken(request));
            AuthSessionHolder.set(session);
            filterChain.doFilter(request, response);
        } catch (AuthException error) {
            writeAuthError(response, error);
        } finally {
            AuthSessionHolder.clear();
        }
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return "/auth/login".equals(path) || "/auth/guest-login".equals(path);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED, "请先登录后再访问实验室资料服务");
        }
        String token = authorization.substring("Bearer ".length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED, "登录状态缺少 token");
        }
        return token;
    }

    private void writeAuthError(HttpServletResponse response, AuthException error) throws IOException {
        HttpStatus status = AuthErrorCode.AUTH_FORBIDDEN.getCode().equals(error.getCode())
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNAUTHORIZED;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(error.getErrorCode(), error.getMessage())));
    }
}
