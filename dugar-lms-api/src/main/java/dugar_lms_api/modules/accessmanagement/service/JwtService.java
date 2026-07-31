package dugar_lms_api.modules.accessmanagement.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private final String jwtSecret;
    private final long jwtExpirationMs;

    public JwtService(
        @Value("${app.jwt.secret}") String jwtSecret,
        @Value("${app.jwt.expiration-ms}") long jwtExpirationMs
    ) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public String generateToken(Long userId, String username, Long roleId, String roleCode) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("roleId", roleId)
            .claim("roleCode", roleCode)
            .issuedAt(issuedAt)
            .expiration(expiration)
            .signWith(buildSigningKey())
            .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Long extractRoleId(String token) {
        return extractClaim(token, claims -> claims.get("roleId", Long.class));
    }

    public String extractRoleCode(String token) {
        return extractClaim(token, claims -> claims.get("roleCode", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private SecretKey buildSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(buildSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = parseClaims(token);
        return claimsResolver.apply(claims);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }
}