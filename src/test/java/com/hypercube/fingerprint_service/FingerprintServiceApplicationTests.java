package com.hypercube.fingerprint_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.application.name=fingerprint-service-test",
    "server.port=0"
})
class FingerprintServiceApplicationTests {

    @Test
    void contextLoads() {
        // This test verifies that the Spring context loads successfully
        // It will test that all beans are properly configured
    }
}
