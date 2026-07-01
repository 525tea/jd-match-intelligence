package jobflow.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtCookieService jwtCookieService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtCookieService jwtCookieService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtCookieService = jwtCookieService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && shouldAuthenticateWithJwt()) {
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        jwtTokenProvider.getPrincipal(token)
                .map(principal -> new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities()
                ))
                .ifPresentOrElse(
                        authentication -> SecurityContextHolder.getContext().setAuthentication(authentication),
                        SecurityContextHolder::clearContext
                );
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }

        return jwtCookieService.resolveAccessToken(request)
                .orElse(null);
    }

    private boolean shouldAuthenticateWithJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal);
    }
}
