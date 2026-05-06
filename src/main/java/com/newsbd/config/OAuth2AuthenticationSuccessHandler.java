package com.newsbd.config;

import com.newsbd.model.User;
import com.newsbd.repository.UserRepository;
import com.newsbd.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    // Your Vercel frontend URL
    private static final String PRODUCTION_URL = "https://newsbd-frontend1.vercel.app";
    private static final String LOCAL_URL = "http://localhost:3000";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found after OAuth2 login"));

        String token = jwtService.generateToken(email, Map.of(
                "role", user.getRole().name(),
                "name", user.getName() != null ? user.getName() : ""
        ));

        // Detect if request comes from production or local
        String referer = request.getHeader("Referer");
        String origin  = request.getHeader("Origin");
        String host    = request.getServerName();

        String frontendUrl;

        // If running on Render (not localhost) → use Vercel URL
        if (host != null && !host.contains("localhost")) {
            frontendUrl = PRODUCTION_URL;
        } else if (referer != null && referer.contains("vercel.app")) {
            frontendUrl = PRODUCTION_URL;
        } else if (origin != null && origin.contains("vercel.app")) {
            frontendUrl = PRODUCTION_URL;
        } else {
            frontendUrl = LOCAL_URL;
        }

        String redirectUrl = frontendUrl + "/oauth2/callback?token=" + token;
        log.info("OAuth2 success for {} — redirecting to: {}", email, frontendUrl);

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}