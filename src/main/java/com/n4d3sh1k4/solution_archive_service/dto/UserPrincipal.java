package com.n4d3sh1k4.solution_archive_service.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserPrincipal(
        UUID id,
        String email,
        Set<String> roles // Храним просто строки: "ROLE_USER", "ROLE_ADMIN"
) {
    // Удобный метод для превращения строк в объекты Spring Security
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}
