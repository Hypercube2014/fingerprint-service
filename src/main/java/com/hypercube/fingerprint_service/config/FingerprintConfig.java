package com.hypercube.fingerprint_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class FingerprintConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
    
    /**
     * Executor for fingerprint device operations
     */
    @Bean(name = "fingerprintDeviceExecutor")
    public Executor fingerprintDeviceExecutor() {
        return new ThreadPoolExecutor(
                2, // core pool size
                4, // maximum pool size
                60L, TimeUnit.SECONDS, // keep alive time
                new LinkedBlockingQueue<>(100), // queue
                r -> {
                    Thread t = new Thread(r, "FingerprintDevice-");
                    t.setDaemon(true);
                    return t;
                }
        );
    }
    
    /**
     * Executor for fingerprint preview operations
     */
    @Bean(name = "fingerprintPreviewExecutor")
    public Executor fingerprintPreviewExecutor() {
        return new ThreadPoolExecutor(
                1, // core pool size
                2, // maximum pool size
                60L, TimeUnit.SECONDS, // keep alive time
                new LinkedBlockingQueue<>(50), // queue
                r -> {
                    Thread t = new Thread(r, "FingerprintPreview-");
                    t.setDaemon(true);
                    return t;
                }
        );
    }
    
    /**
     * Executor for heavy fingerprint operations
     */
    @Bean(name = "fingerprintHeavyExecutor")
    public Executor fingerprintHeavyExecutor() {
        return new ThreadPoolExecutor(
                1, // core pool size
                2, // maximum pool size
                120L, TimeUnit.SECONDS, // keep alive time
                new LinkedBlockingQueue<>(20), // queue
                r -> {
                    Thread t = new Thread(r, "FingerprintHeavy-");
                    t.setDaemon(true);
                    return t;
                }
        );
    }
}

