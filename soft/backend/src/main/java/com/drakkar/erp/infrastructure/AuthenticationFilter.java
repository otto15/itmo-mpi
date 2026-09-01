package com.drakkar.erp.infrastructure;

import com.drakkar.erp.api.ApiModels;
import com.drakkar.erp.application.AuthService;
import com.drakkar.erp.domain.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthenticationFilter extends OncePerRequestFilter {
    public static final String USER_ATTRIBUTE = AuthenticationFilter.class.getName() + ".USER";

    private final AuthService auth;
    private final ObjectMapper json;

    public AuthenticationFilter(AuthService auth, ObjectMapper json) {
        this.auth = auth;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().equals("/api/auth/login")
                || request.getRequestURI().equals("/api/provisioning/settlements")
                || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            unauthorized(response, "AUTH_REQUIRED", "Требуется вход в систему");
            return;
        }
        try {
            request.setAttribute(USER_ATTRIBUTE, auth.authenticate(header.substring(7)));
            filterChain.doFilter(request, response);
        } catch (DomainException exception) {
            unauthorized(response, exception.code(), exception.getMessage());
        }
    }

    private void unauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), new ApiModels.ErrorResponse(code, message, Instant.now()));
    }
}
