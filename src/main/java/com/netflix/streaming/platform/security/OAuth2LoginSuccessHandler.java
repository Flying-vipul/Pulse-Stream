package com.netflix.streaming.platform.security;

import com.netflix.streaming.platform.model.Role;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.security.jwt.JwtUtils;
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

import java.io.IOException;
import java.util.UUID;

/**
 * Handles a successful Google OAuth2 login.
 *
 * <p>Security notes:
 * <ul>
 *   <li>JWT is built by {@link JwtUtils#generateJwtTokenFromUser(User)}, which reads the role
 *       from the database record — never from OAuth2 claims. This prevents role escalation/downgrade.</li>
 *   <li>An existing user''s role is NEVER overwritten on OAuth login — the DB value is preserved.</li>
 *   <li>The JWT is appended to the redirect URL as a query parameter. This approach carries known risks
 *       (browser history, Referer headers, proxy logs). A safer code-exchange flow will be implemented
 *       when the frontend is updated. Until then, the token is not logged anywhere.</li>
 *   <li>JWT claims contain only subject (email) and role — no userId, name, or other PII.</li>
 * </ul>
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

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

        if (email == null || email.isBlank()) {
            log.error("OAuth2 login failed: no email attribute returned from provider");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not provided by OAuth2 provider");
            return;
        }

        // Find or create the user. CRITICAL: if the user already exists, their role is
        // NEVER changed — the existing DB value is preserved. This prevents a scenario
        // where an admin loses ROLE_ADMIN after logging in via Google.
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("New Google OAuth2 user — creating account for email hash: {}",
                    Integer.toHexString(email.hashCode()));
            User newUser = new User(name, email, UUID.randomUUID().toString()); // random unusable password
            newUser.setVerified(true);   // Google already verified the email
            newUser.setRole(Role.ROLE_USER);
            return userRepository.save(newUser);
        });

        log.info("Google OAuth2 login success for user id={}", user.getId());

        // Delegate token generation to JwtUtils — single source of truth for signing logic.
        // Role is read from user.getRole() (DB), not from any OAuth2 claim.
        // SECURITY: Token is NOT logged here.
        String token = jwtUtils.generateJwtTokenFromUser(user);

        // TODO (security): Replace this with a short-lived one-time code exchange when
        // the frontend is updated. Current approach appends JWT to the redirect URL,
        // which exposes it in browser history and server access logs.
        String targetUrl = frontendUrl + "/oauth2/redirect?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}