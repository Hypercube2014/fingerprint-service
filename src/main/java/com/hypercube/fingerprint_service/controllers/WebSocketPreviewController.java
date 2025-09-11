package com.hypercube.fingerprint_service.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebSocketPreviewController {
    
    /**
     * Serve the fingerprint preview HTML page
     */
    @GetMapping("/preview")
    public String previewPage() {
        return "redirect:/fingerprint-preview.html";
    }
    
    /**
     * Direct access to the preview page
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/fingerprint-preview.html";
    }
}
