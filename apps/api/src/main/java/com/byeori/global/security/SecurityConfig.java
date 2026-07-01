package com.byeori.global.security;

import com.byeori.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless 보안 설정. JWT 필터로 인증, CSRF off, CORS는 WebConfig와 동일 정책을 Security에도 연결.
 * 보호 규칙(authenticated)이 우선, 명시 공개(permitAll), 나머지(anyRequest) 공개.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // --- 보호 경로(authenticated)가 먼저 매칭되도록 상단 배치 ---
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/**").authenticated()
                        .requestMatchers("/api/v1/wishlists/**").authenticated()
                        .requestMatchers("/api/v1/itineraries/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/venues/mine").authenticated()
                        // 리뷰/장소 쓰기
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews", "/api/v1/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/venues", "/api/v1/venues/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/venues/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/venues/**").authenticated()
                        // 이미지 업로드(POST)는 로그인 필요. 서빙(GET)은 아래 공개.
                        .requestMatchers(HttpMethod.POST, "/api/v1/uploads/**").authenticated()

                        // --- 공개 경로(permitAll) ---
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/sync/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/venues/**",
                                "/api/v1/performances/**",
                                "/api/v1/curated-courses/**",
                                "/api/v1/content-tags/**",
                                "/api/v1/comment-tags/**").permitAll()

                        // 나머지 공개
                        .anyRequest().permitAll())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(this::unauthorized))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void unauthorized(jakarta.servlet.http.HttpServletRequest request,
                              HttpServletResponse response,
                              org.springframework.security.core.AuthenticationException ex)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail("UNAUTHORIZED", "인증이 필요합니다.")));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
