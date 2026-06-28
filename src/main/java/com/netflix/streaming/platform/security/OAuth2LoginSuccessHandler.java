package com.netflix.streaming.platform.security;

import com.netflix.streaming.platform.model.Role;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.security.jwt.JwtUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private int jwtExpirationMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(JwtUtils jwtUtils, UserRepository userRepository) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String rawName = oAuth2User.getAttribute("name");
        final String name = (rawName != null && !rawName.isBlank()) ? rawName : email;

        // ── Find or create the user in PostgreSQL ─────────────────────────────
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("🆕 New Google user — creating account for: {}", email);
            User newUser = new User(name, email, UUID.randomUUID().toString()); // random unusable password
            newUser.setVerified(true); // Google already verified the email
            newUser.setRole(Role.ROLE_USER);
            return userRepository.save(newUser);
        });

        log.info("✅ Google OAuth2 login for user id={} email={}", user.getId(), email);

        // ── Generate JWT with full user info as claims ────────────────────────
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        String token = Jwts.builder()
                .subject(email)
                .claim("userId", user.getId())
                .claim("name",   user.getName())
                .claim("email",  email)
                .claim("role",   user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key)
                .compact();

        // Redirect to React app on port 5173 or deployed frontend
        String targetUrl = frontendUrl + "/oauth2/redirect?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}