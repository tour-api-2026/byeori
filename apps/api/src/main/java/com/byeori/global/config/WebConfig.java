package com.byeori.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // 개인정보처리방침 — Play Store 등록용 공개 URL (/privacy → static/privacy.html)
        registry.addViewController("/privacy").setViewName("forward:/privacy.html");
        // 계정 삭제 안내 — Play 데이터 보안 '계정 삭제 URL' 요건
        registry.addViewController("/account-deletion").setViewName("forward:/account-deletion.html");
    }
}
