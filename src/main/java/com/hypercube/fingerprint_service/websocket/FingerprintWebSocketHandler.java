package com.hypercube.fingerprint_service.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypercube.fingerprint_service.services.FingerprintDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class FingerprintWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(FingerprintWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Store active WebSocket sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Store preview state for each session
    private final Map<String, PreviewState> previewStates = new ConcurrentHashMap<>();
    
    // Scheduled executor for preview streaming
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    @Autowired
    private FingerprintDeviceService deviceService;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        previewStates.put(sessionId, new PreviewState());
        
        logger.info("WebSocket connection established: {}", sessionId);
        
        // Send welcome message
        sendMessage(session, createMessage("connection", Map.of(
            "status", "connected",
            "session_id", sessionId,
            "message", "Connected to BIO600 Fingerprint WebSocket Service"
        )));
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String sessionId = session.getId();
        String payload = (String) message.getPayload();
        
        logger.debug("Received message from {}: {}", sessionId, payload);
        
        try {
            // Parse JSON message
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String command = (String) messageData.get("command");
            
            switch (command) {
                case "start_preview":
                    handleStartPreview(session, sessionId, messageData);
                    break;
                case "stop_preview":
                    handleStopPreview(session, sessionId);
                    break;
                case "capture":
                    handleCapture(session, sessionId, messageData);
                    break;
                case "capture_template":
                    handleCaptureTemplate(session, sessionId, messageData);
                    break;
                case "get_status":
                    handleGetStatus(session, sessionId);
                    break;
                default:
                    sendError(session, "Unknown command: " + command);
            }
            
        } catch (Exception e) {
            logger.error("Error handling message from {}: {}", sessionId, e.getMessage(), e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }
    
    private void handleStartPreview(WebSocketSession session, String sessionId, Map<String, Object> messageData) {
        try {
            PreviewState state = previewStates.get(sessionId);
            if (state.isPreviewRunning()) {
                sendError(session, "Preview already running");
                return;
            }
            
            // Get parameters with defaults
            int channel = getIntValue(messageData, "channel", 0);
            int width = getIntValue(messageData, "width", 1600);
            int height = getIntValue(messageData, "height", 1500);
            
            logger.info("Starting preview for session {}: channel={}, dimensions={}x{}", 
                sessionId, channel, width, height);
            
            // Start preview stream in device service
            boolean success = deviceService.startPreviewStream(channel, width, height);
            
            if (success) {
                state.setPreviewRunning(true);
                state.setChannel(channel);
                state.setWidth(width);
                state.setHeight(height);
                
                // Start sending preview frames
                startPreviewStreaming(session, sessionId, channel);
                
                sendMessage(session, createMessage("preview_started", Map.of(
                    "success", true,
                    "channel", channel,
                    "width", width,
                    "height", height,
                    "fps", 15
                )));
            } else {
                sendError(session, "Failed to start preview stream");
            }
            
        } catch (Exception e) {
            logger.error("Error starting preview for session {}: {}", sessionId, e.getMessage(), e);
            sendError(session, "Error starting preview: " + e.getMessage());
        }
    }
    
    private void handleStopPreview(WebSocketSession session, String sessionId) {
        try {
            PreviewState state = previewStates.get(sessionId);
            if (!state.isPreviewRunning()) {
                sendError(session, "Preview not running");
                return;
            }
            
            int channel = state.getChannel();
            logger.info("Stopping preview for session {}: channel={}", sessionId, channel);
            
            // Stop preview stream
            deviceService.stopPreviewStream(channel);
            state.setPreviewRunning(false);
            
            sendMessage(session, createMessage("preview_stopped", Map.of(
                "success", true,
                "channel", channel
            )));
            
        } catch (Exception e) {
            logger.error("Error stopping preview for session {}: {}", sessionId, e.getMessage(), e);
            sendError(session, "Error stopping preview: " + e.getMessage());
        }
    }
    
    private void handleCapture(WebSocketSession session, String sessionId, Map<String, Object> messageData) {
        try {
            int channel = getIntValue(messageData, "channel", 0);
            int width = getIntValue(messageData, "width", 1600);
            int height = getIntValue(messageData, "height", 1500);
            
            logger.info("Capturing fingerprint for session {}: channel={}, dimensions={}x{}", 
                sessionId, channel, width, height);
            
            // Capture high-quality image
            Map<String, Object> captureResult = deviceService.captureFingerprint(channel, width, height);
            
            if (captureResult != null && Boolean.TRUE.equals(captureResult.get("success"))) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("imageData", captureResult.get("image"));
                responseData.put("quality", captureResult.get("quality_score"));
                responseData.put("width", width);
                responseData.put("height", height);
                responseData.put("channel", channel);
                responseData.put("storage_info", captureResult.get("storage_info"));
                
                sendMessage(session, createMessage("capture_result", responseData));
            } else {
                String errorMsg = "Capture failed";
                if (captureResult != null && captureResult.get("error_details") != null) {
                    errorMsg += ": " + captureResult.get("error_details");
                }
                sendError(session, errorMsg);
            }
            
        } catch (Exception e) {
            logger.error("Error capturing fingerprint for session {}: {}", sessionId, e.getMessage(), e);
            sendError(session, "Error capturing fingerprint: " + e.getMessage());
        }
    }
    
    private void handleCaptureTemplate(WebSocketSession session, String sessionId, Map<String, Object> messageData) {
        try {
            int channel = getIntValue(messageData, "channel", 0);
            int width = getIntValue(messageData, "width", 1600);
            int height = getIntValue(messageData, "height", 1500);
            String format = (String) messageData.get("format");
            
            if (format == null || format.isEmpty()) {
                sendError(session, "Template format not specified");
                return;
            }
            
            logger.info("Capturing template for session {}: channel={}, dimensions={}x{}, format={}", 
                sessionId, channel, width, height, format);
            
            // Capture fingerprint template
            Map<String, Object> templateResult = deviceService.captureFingerprintTemplate(channel, width, height, format);
            
            if (templateResult != null && Boolean.TRUE.equals(templateResult.get("success"))) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("template_format", templateResult.get("template_format"));
                responseData.put("template_size", templateResult.get("template_size"));
                responseData.put("template_data", templateResult.get("template_data"));
                responseData.put("iso_template", templateResult.get("iso_template"));
                responseData.put("ansi_template", templateResult.get("ansi_template"));
                responseData.put("quality_score", templateResult.get("quality_score"));
                responseData.put("width", width);
                responseData.put("height", height);
                responseData.put("channel", channel);
                
                sendMessage(session, createMessage("template_result", responseData));
            } else {
                String errorMsg = "Template capture failed";
                if (templateResult != null && templateResult.get("error_details") != null) {
                    errorMsg += ": " + templateResult.get("error_details");
                }
                // FIXED: Send single error message to avoid TEXT_PARTIAL_WRITING error
                sendMessage(session, createMessage("error", Map.of("message", errorMsg)));
            }
            
        } catch (Exception e) {
            logger.error("Error capturing template for session {}: {}", sessionId, e.getMessage(), e);
            // FIXED: Send single error message to avoid TEXT_PARTIAL_WRITING error
            sendMessage(session, createMessage("error", Map.of("message", "Error capturing template: " + e.getMessage())));
        }
    }
    
    private void handleGetStatus(WebSocketSession session, String sessionId) {
        try {
            PreviewState state = previewStates.get(sessionId);
            Map<Integer, Boolean> deviceStatus = deviceService.getDeviceStatus();
            
            sendMessage(session, createMessage("status", Map.of(
                "session_id", sessionId,
                "preview_running", state.isPreviewRunning(),
                "channel", state.getChannel(),
                "device_status", deviceStatus,
                "platform_info", deviceService.getPlatformInfo(),
                "supported", deviceService.isPlatformSupported()
            )));
            
        } catch (Exception e) {
            logger.error("Error getting status for session {}: {}", sessionId, e.getMessage(), e);
            sendError(session, "Error getting status: " + e.getMessage());
        }
    }
    
    private void startPreviewStreaming(WebSocketSession session, String sessionId, int channel) {
        // Schedule preview frame sending at 15 FPS (every 66ms)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!session.isOpen() || !previewStates.get(sessionId).isPreviewRunning()) {
                    return;
                }
                
                // Get current preview frame
                Map<String, Object> frameData = deviceService.getCurrentPreviewFrame(channel);
                
                if (frameData != null && (Boolean) frameData.get("success")) {
                    // Send preview frame
                    sendMessage(session, createMessage("preview", Map.of(
                        "imageData", frameData.get("image_data"),
                        "quality", frameData.get("quality"),
                        "width", frameData.get("width"),
                        "height", frameData.get("height"),
                        "has_finger", frameData.get("has_finger"),
                        "timestamp", frameData.get("frame_timestamp")
                    )));
                }
                
            } catch (Exception e) {
                logger.error("Error in preview streaming for session {}: {}", sessionId, e.getMessage());
                // Stop preview on error
                previewStates.get(sessionId).setPreviewRunning(false);
            }
        }, 0, 66, TimeUnit.MILLISECONDS); // ~15 FPS
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        logger.error("WebSocket transport error for session {}: {}", sessionId, exception.getMessage(), exception);
        
        // Clean up session
        cleanupSession(sessionId);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        logger.info("WebSocket connection closed: {} - {}", sessionId, closeStatus);
        
        // Clean up session
        cleanupSession(sessionId);
    }
    
    private void cleanupSession(String sessionId) {
        try {
            PreviewState state = previewStates.get(sessionId);
            if (state != null && state.isPreviewRunning()) {
                deviceService.stopPreviewStream(state.getChannel());
            }
        } catch (Exception e) {
            logger.error("Error cleaning up session {}: {}", sessionId, e.getMessage());
        } finally {
            sessions.remove(sessionId);
            previewStates.remove(sessionId);
        }
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    private synchronized void sendMessage(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            logger.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
        }
    }
    
    private void sendError(WebSocketSession session, String errorMessage) {
        sendMessage(session, createMessage("error", Map.of(
            "message", errorMessage,
            "timestamp", System.currentTimeMillis()
        )));
    }
    
    private String createMessage(String type, Map<String, Object> data) {
        try {
            Map<String, Object> message = Map.of(
                "type", type,
                "data", data,
                "timestamp", System.currentTimeMillis()
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            logger.error("Error creating message: {}", e.getMessage());
            return "{\"type\":\"error\",\"data\":{\"message\":\"Error creating message\"}}";
        }
    }
    
    private int getIntValue(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    // Inner class to track preview state for each session
    private static class PreviewState {
        private boolean previewRunning = false;
        private int channel = 0;
        private int width = 1600;
        private int height = 1500;
        
        public boolean isPreviewRunning() { return previewRunning; }
        public void setPreviewRunning(boolean previewRunning) { this.previewRunning = previewRunning; }
        
        public int getChannel() { return channel; }
        public void setChannel(int channel) { this.channel = channel; }
        
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }
}
