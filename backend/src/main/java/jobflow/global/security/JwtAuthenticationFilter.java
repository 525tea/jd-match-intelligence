package jobflow.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
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
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        if (!jwtTokenProvider.isValidToken(token)) {
            SecurityContextHolder.clearContext();
            return;
        }

        Long userId = jwtTokenProvider.getUserId(token);
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            SecurityContextHolder.clearContext();
            return;
        }

        UserPrincipal principal = UserPrincipal.from(user);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorization.substring(BEARER_PREFIX.length());
    }
}
