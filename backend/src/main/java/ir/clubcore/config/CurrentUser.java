package ir.clubcore.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import ir.clubcore.common.BusinessException;
import ir.clubcore.user.Role;

/** Reads the authenticated user from the JWT in the security context. */
@Component
public class CurrentUser {

    public Long id() {
        return Long.valueOf(jwt().getSubject());
    }

    public Role role() {
        return Role.valueOf(jwt().getClaimAsString("role"));
    }

    public boolean is(Role... roles) {
        Role r = role();
        for (Role role : roles) {
            if (role == r) {
                return true;
            }
        }
        return false;
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "ابتدا وارد شوید");
    }
}
