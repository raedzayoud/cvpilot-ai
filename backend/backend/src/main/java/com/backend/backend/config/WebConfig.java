package com.backend.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;
    private final AiRateLimitInterceptor aiRateLimit;

    public WebConfig(AppProperties properties, AiRateLimitInterceptor aiRateLimit) {
        this.properties = properties;
        this.aiRateLimit = aiRateLimit;
    }

    /** Only the configured frontend origins may call the API from a browser. No credentials are used. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT)
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.RETRY_AFTER)
                .allowCredentials(false)
                .maxAge(3600);
    }

    /** Endpoints whose handling calls the AI provider. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiRateLimit)
                .addPathPatterns("/api/cvs", "/api/cvs/text", "/api/jobs", "/api/jobs/upload", "/api/analyses/*/chat",
                        "/api/agent/sessions/*/messages", "/api/agent/proposals/*/regenerate");
    }
}
