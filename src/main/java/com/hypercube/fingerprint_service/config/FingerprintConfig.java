package com.hypercube.fingerprint_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
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
    
    @Bean("fingerprintDeviceExecutor")
    public Executor fingerprintDeviceExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, // core pool size
                4, // maximum pool size
                60L, TimeUnit.SECONDS, // keep alive time
                new LinkedBlockingQueue<>(100), // queue capacity
                r -> new Thread(r, "fingerprint-device-" + System.currentTimeMillis())
        );
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
    
    @Bean("fingerprintPreviewExecutor")
    public Executor fingerprintPreviewExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, // core pool size
                2, // maximum pool size
                60L, TimeUnit.SECONDS, // keep alive time
                new LinkedBlockingQueue<>(50), // queue capacity
                r -> new Thread(r, "fingerprint-preview-" + System.currentTimeMillis())
        );
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
    
    @Bean("fingerprintHeavyExecutor")
    public Executor fingerprintHeavyExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, // core pool size
                2, // maximum pool size
                300L, TimeUnit.SECONDS, // keep alive time
                new LinkedBlockingQueue<>(20), // queue capacity
                r -> new Thread(r, "fingerprint-heavy-" + System.currentTimeMillis())
        );
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}

