package com.newsbd.config;

import com.newsbd.service.CustomOAuth2UserService;
import com.newsbd.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,  "/api/news/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/news/*/view").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/ai/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**", "/api/auth/**").permitAll()
                        .requestMatchers("/api/bookmarks/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(ep -> ep.userService(oAuth2UserService))
                        .successHandler(successHandler)
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public OncePerRequestFilter jwtAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain)
                    throws IOException, ServletException {

                // ✅ Allow ALL origins — fixes CORS for Vercel
                String origin = req.getHeader("Origin");
                if (origin != null) {
                    res.setHeader("Access-Control-Allow-Origin", origin);
                } else {
                    res.setHeader("Access-Control-Allow-Origin", "*");
                }
                res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
                res.setHeader("Access-Control-Allow-Credentials", "true");
                res.setHeader("Access-Control-Max-Age", "3600");

                // Handle preflight OPTIONS request
                if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
                    res.setStatus(HttpServletResponse.SC_OK);
                    return;
                }

                // JWT Authentication
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    try {
                        String email = jwtService.extractEmail(token);
                        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                            var userDetails = userDetailsService.loadUserByUsername(email);
                            if (jwtService.isValid(token, userDetails)) {
                                var authToken = new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities()
                                );
                                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                                SecurityContextHolder.getContext().setAuthentication(authToken);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                chain.doFilter(req, res);
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow ALL origins — works for any domain
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(Arrays.asList("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}