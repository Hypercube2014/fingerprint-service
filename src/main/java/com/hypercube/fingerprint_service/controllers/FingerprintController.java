package com.hypercube.fingerprint_service.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hypercube.fingerprint_service.services.FingerprintDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@RestController
@RequestMapping("/api/fingerprint")
public class FingerprintController {

    private static final Logger logger = LoggerFactory.getLogger(FingerprintController.class);

    @Autowired
    private FingerprintDeviceService deviceService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "BIO600 Fingerprint Service",
            "timestamp", System.currentTimeMillis()
        ));
    }

    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initializeDevice(@RequestParam(defaultValue = "0") int channel) {
        try {
            boolean success = deviceService.initializeDevice(channel);
            return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Device initialized successfully" : "Device initialization failed",
                "channel", channel
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error initializing device: " + e.getMessage(),
                "channel", channel
            ));
        }
    }

    @PostMapping("/capture")
    public ResponseEntity<Map<String, Object>> captureFingerprint(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        try {
            Map<String, Object> result = deviceService.captureFingerprint(channel, width, height);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error capturing fingerprint: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/split/four-right")
    public ResponseEntity<Map<String, Object>> splitFourRightFingers(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height,
            @RequestParam(defaultValue = "300") int splitWidth,
            @RequestParam(defaultValue = "400") int splitHeight) {
        try {
            Map<String, Object> result = deviceService.splitFourRightFingers(channel, width, height, splitWidth, splitHeight);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error splitting four right fingers: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/split/thumbs")
    public ResponseEntity<Map<String, Object>> splitTwoThumbs(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height,
            @RequestParam(defaultValue = "300") int splitWidth,
            @RequestParam(defaultValue = "400") int splitHeight) {
        try {
            Map<String, Object> result = deviceService.splitTwoThumbs(channel, width, height, splitWidth, splitHeight);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error splitting two thumbs: " + e.getMessage()
            ));
        }
    }
}
