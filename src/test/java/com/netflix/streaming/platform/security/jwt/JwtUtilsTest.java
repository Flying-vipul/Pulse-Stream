package com.netflix.streaming.platform.security.jwt;

import com.netflix.streaming.platform.model.Role;
import com.netflix.streaming.platform.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.Key;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private final String jwtSecret = "mySecretKeyThatIsVeryLongAndSecureForTesting1234567890";
    private final int jwtExpirationMs = 86400000;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", jwtExpirationMs);
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    @Test
    void testGenerateJwtTokenFromUser() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setRole(Role.ROLE_USER);

        String token = jwtUtils.generateJwtTokenFromUser(user);

        assertNotNull(token);
        assertEquals("test@example.com", jwtUtils.getEmailByJwtToken(token));
        assertTrue(jwtUtils.validateJwtToken(token));
    }

    @Test
    void testValidateJwtToken_Malformed() {
        assertFalse(jwtUtils.validateJwtToken("this.is.not.a.valid.jwt"));
    }

    @Test
    void testValidateJwtToken_Expired() {
        String expiredToken = Jwts.builder()
                .setSubject("test@example.com")
                .setIssuedAt(new Date(System.currentTimeMillis() - 10000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();

        assertFalse(jwtUtils.validateJwtToken(expiredToken));
    }
}
