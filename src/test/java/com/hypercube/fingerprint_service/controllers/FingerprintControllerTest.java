package com.hypercube.fingerprint_service.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.hypercube.fingerprint_service.services.FingerprintDeviceService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FingerprintController.class)
class FingerprintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FingerprintDeviceService deviceService;

    @Test
    void healthCheck_ShouldReturnHealthyStatus() throws Exception {
        mockMvc.perform(get("/api/fingerprint/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("BIO600 Fingerprint Service"));
    }

    @Test
    void initializeDevice_ShouldReturnSuccessResponse() throws Exception {
        // This test would require mocking the device service
        // For now, we'll just test the endpoint structure
        mockMvc.perform(post("/api/fingerprint/init")
                .param("channel", "0"))
                .andExpect(status().isOk());
    }

    @Test
    void captureFingerprint_ShouldReturnSuccessResponse() throws Exception {
        // This test would require mocking the device service
        // For now, we'll just test the endpoint structure
        mockMvc.perform(post("/api/fingerprint/capture")
                .param("channel", "0")
                .param("width", "1600")
                .param("height", "1500"))
                .andExpect(status().isOk());
    }
}

