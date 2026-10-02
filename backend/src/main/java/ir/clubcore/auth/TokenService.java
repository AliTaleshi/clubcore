package ir.clubcore.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.config.AppProperties;
import ir.clubcore.user.User;
import ir.clubcore.user.UserDto;
import ir.clubcore.user.UserRepository;

@Service
public class TokenService {

    public record Tokens(String accessToken, String refreshToken, long expiresIn, UserDto user) {
    }

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AppProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, UserRepository users,
            AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Tokens issue(User user) {
        Instant now = clock.instant();
        Duration ttl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("clubcore")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(Hashing.sha256(refresh));
        rt.setExpiresAt(now.plus(Duration.ofDays(props.jwt().refreshTtlDays())));
        refreshTokens.save(rt);
        return new Tokens(access, refresh, ttl.toSeconds(), UserDto.of(user));
    }

    /** Rotates a refresh token: the old one is revoked and a fresh pair is issued. */
    @Transactional
    public Tokens refresh(String refreshToken) {
        RefreshToken rt = refreshTokens.findByTokenHash(Hashing.sha256(refreshToken))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "نشست شما منقضی شده است، دوباره وارد شوید"));
        rt.setRevoked(true);
        User user = users.findById(rt.getUserId()).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "حساب کاربری غیرفعال است"));
        return issue(user);
    }

    @Transactional
    public void revoke(String refreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(refreshToken)).ifPresent(t -> t.setRevoked(true));
    }
}
