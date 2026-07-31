package dugar_lms_api.common.security;

import dugar_lms_api.modules.accessmanagement.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String method = request.getMethod();

        return (HttpMethod.POST.matches(method)
                && "/api/auth/login".equals(servletPath))
            || (HttpMethod.GET.matches(method)
                && "/api/health".equals(servletPath));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        LOGGER.info(
            "Request path={}, Authorization header present={}",
            request.getServletPath(),
            authHeader != null
        );

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            LOGGER.warn(
                "Missing or invalid Authorization header for path={}",
                request.getServletPath()
            );

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isBlank()) {
            LOGGER.warn(
                "Bearer token is empty for path={}",
                request.getServletPath()
            );

            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!jwtService.isTokenValid(token)) {
                LOGGER.warn(
                    "JWT token is invalid or expired for path={}",
                    request.getServletPath()
                );

                filterChain.doFilter(request, response);
                return;
            }

            String username = jwtService.extractUsername(token);
            Long userId = jwtService.extractUserId(token);
            Long roleId = jwtService.extractRoleId(token);
            String roleCode = jwtService.extractRoleCode(token);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                List<GrantedAuthority> authorities =
                    buildAuthorities(roleCode);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                    );

                Map<String, Object> jwtDetails = new LinkedHashMap<>();
                jwtDetails.put("userId", userId);
                jwtDetails.put("roleId", roleId);
                jwtDetails.put("roleCode", roleCode);
                jwtDetails.put("authorities", authorities);
                jwtDetails.put(
                    "request",
                    new WebAuthenticationDetailsSource()
                        .buildDetails(request)
                );

                authentication.setDetails(jwtDetails);

                SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

                LOGGER.info(
                    "JWT authenticated username={}, roleCode={}, authorities={}, path={}",
                    authentication.getName(),
                    roleCode,
                    authentication.getAuthorities(),
                    request.getServletPath()
                );
            }

        } catch (Exception exception) {
            SecurityContextHolder.clearContext();

            LOGGER.error(
                "JWT authentication failed for path={}",
                request.getServletPath(),
                exception
            );
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> buildAuthorities(String roleCode) {
        String authority = toRoleAuthority(roleCode);

        if (authority == null) {
            return Collections.emptyList();
        }

        return List.of(new SimpleGrantedAuthority(authority));
    }

    private String toRoleAuthority(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }

        String normalizedRoleCode =
            roleCode.trim().toUpperCase(Locale.ROOT);

        if (normalizedRoleCode.startsWith(ROLE_PREFIX)) {
            normalizedRoleCode =
                normalizedRoleCode.substring(ROLE_PREFIX.length());
        }

        // Example:
        // SUPER_ADMIN_1 becomes SUPER_ADMIN
        normalizedRoleCode =
            normalizedRoleCode.replaceFirst("_\\d+$", "");

        return ROLE_PREFIX + normalizedRoleCode;
    }
}