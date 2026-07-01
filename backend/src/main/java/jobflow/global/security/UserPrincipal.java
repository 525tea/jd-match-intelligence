package jobflow.global.security;

import java.util.Collection;
import java.util.List;
import jobflow.domain.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record UserPrincipal(
        Long id,
        String email,
        String name,
        String role
) {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
    }

    public static UserPrincipal fromClaims(
            Long id,
            String email,
            String name,
            String role
    ) {
        return new UserPrincipal(
                id,
                email,
                name == null || name.isBlank() ? email : name,
                role
        );
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
