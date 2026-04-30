package com.n4d3sh1k4.solution_archive_service.filter;

import com.n4d3sh1k4.solution_archive_service.dto.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Component
public class InternalAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rolesHeader = request.getHeader("X-User-Roles");

        if (rolesHeader != null) {
            UUID userId = UUID.fromString(request.getHeader("X-User-Id"));
            Set<String> roles = Set.of(rolesHeader.split(","));
            UserPrincipal principal = new UserPrincipal(userId, null, roles);
            // Передаем authorities в токен, чтобы Spring Security их увидел
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}