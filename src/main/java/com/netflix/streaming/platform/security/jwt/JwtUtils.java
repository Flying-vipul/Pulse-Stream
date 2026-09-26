package com.netflix.streaming.platform.security.jwt;

import com.netflix.streaming.platform.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private int jwtExpirationMs;

    // 1. EXTRACT JWT FROM HEADER
    // SECURITY: Never log the raw bearer token value.
    public String getJwtFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        logger.debug("Authorization header present: {}", bearerToken != null);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // 2. GENERATE TOKEN FROM UserDetails (used by login flow)
    public String generateJwtToken(UserDetails userDetails) {
        String email = userDetails.getUsername();
        // Role is sourced from the loaded UserDetails, which was built from the DB record.
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    // 3. GENERATE TOKEN FROM User entity (used by OAuth2 success handler)
    // Role is always read from the persisted User entity, never from OAuth2 claims.
    // This prevents an attacker from downgrading or upgrading roles via OAuth2.
    public String generateJwtTokenFromUser(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    // 4. EXTRACT EMAIL FROM JWT
    public String getEmailByJwtToken(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // 5. SIGNING KEY (HMAC-SHA256, loaded once per token operation)
    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    // 6. VALIDATE JWT TOKEN
    // SECURITY: Log only the exception type/category, never the raw token or message
    // which may contain sensitive fragments.
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith((SecretKey) key())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.warn("JWT validation failed: malformed token");
        } catch (ExpiredJwtException e) {
            logger.warn("JWT validation failed: token expired");
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT validation failed: unsupported token format");
        } catch (IllegalArgumentException e) {
            logger.warn("JWT validation failed: empty or null token string");
        }
        return false;
    }
}