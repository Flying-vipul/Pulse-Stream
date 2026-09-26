package com.netflix.streaming.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ResourceWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/videos/**")
                .addResourceLocations("file:///C:/Users/Vipul/Videos/PulseStream/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/videos/**")
                .allowedOrigins("http://localhost:5173", "https://thepulsestream.netlify.app") // More secure: explicitly allow your React port
                .allowedMethods("GET", "OPTIONS");
    }
}