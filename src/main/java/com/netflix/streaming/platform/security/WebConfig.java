package com.netflix.streaming.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // You can change this to your actual Vercel/Netlify URL later when you deploy React!
    @Value("${frontend.url:http://localhost:5173}")
    private String frontEndURL;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:5173", // React/Vite default
                        "http://localhost:3000", // Create React App default
                        "https://pulsestream.netlify.app", // Your future production URL
                        frontEndURL
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}