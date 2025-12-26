package org.example.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss");
        registry.addConverter(String.class, LocalDateTime.class, s -> LocalDateTime.parse(s, formatter));
    }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 路径映射到文件系统中的 uploads 目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

//    @Override
//    public void addFormatters(FormatterRegistry registry) {
//        registry.addConverter(String.class, LocalDateTime.class, source ->
//                LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
//    }
}