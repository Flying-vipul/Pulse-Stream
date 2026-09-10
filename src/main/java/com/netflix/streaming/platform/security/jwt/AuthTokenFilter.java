package com.netflix.streaming.platform.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    /**
     * Bypass JWT processing for public media paths.
     * Spring Security pattern matchers can choke on URL-encoded special chars
     * in HLS segment filenames (e.g., stream_%03d.ts), causing permitAll() to
     * be bypassed. Returning true here prevents those requests from ever reaching
     * the auth logic.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/videos/")
                || path.startsWith("/images/")
                || path.startsWith("/avatars/")
                || path.startsWith("/streams/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        logger.debug("AuthTokenFilter processing URI: {}", request.getRequestURI());

        try {
            String jwt = jwtUtils.getJwtFromHeader(request);

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String email = jwtUtils.getEmailByJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Authentication set for user: [email hidden], authorities: {}",
                        userDetails.getAuthorities());
            }
        } catch (UsernameNotFoundException e) {
            // Token contained an email that no longer exists in the DB.
            // Log at WARN but do NOT set authentication — the request proceeds
            // as anonymous and Spring Security will enforce authorization rules.
            logger.warn("JWT referenced unknown user — proceeding as anonymous");
        } catch (Exception e) {
            // Catch-all for any unexpected parsing/loading failure.
            // SECURITY: Do not log the exception message; it may contain token fragments.
            logger.warn("Failed to process JWT — proceeding as anonymous. Cause type: {}",
                    e.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }
}