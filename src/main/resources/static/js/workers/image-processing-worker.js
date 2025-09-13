/**
 * Image Processing Web Worker
 * Handles CPU-intensive image processing operations without blocking the main thread
 */

// Listen for messages from the main thread
self.onmessage = function(e) {
    const { type, data } = e.data;
    
    try {
        switch (type) {
            case 'process_fingerprint_image':
                processFingerprintImage(data);
                break;
            case 'decode_base64':
                decodeBase64Image(data);
                break;
            case 'enhance_image':
                enhanceImage(data);
                break;
            default:
                self.postMessage({
                    success: false,
                    error: `Unknown operation type: ${type}`
                });
        }
    } catch (error) {
        self.postMessage({
            success: false,
            error: error.message
        });
    }
};

/**
 * Process fingerprint image with vertical flip and enhancement
 */
function processFingerprintImage(data) {
    const { base64Data, width, height, options = {} } = data;
    
    try {
        // Decode base64 to bytes
        const binaryString = atob(base64Data);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        
        // Apply vertical flip (critical for proper display)
        if (options.applyVerticalFlip !== false) {
            applyVerticalFlip(bytes, width, height);
        }
        
        // Apply image enhancement
        if (options.enhanceImage !== false) {
            enhanceImageData(bytes, width, height);
        }
        
        // Convert back to base64
        const enhancedBase64 = btoa(String.fromCharCode.apply(null, bytes));
        
        // Send result back to main thread
        self.postMessage({
            success: true,
            type: 'fingerprint_image_processed',
            data: {
                enhancedImageData: enhancedBase64,
                width: width,
                height: height,
                originalSize: base64Data.length,
                processedSize: enhancedBase64.length
            }
        });
        
    } catch (error) {
        self.postMessage({
            success: false,
            type: 'fingerprint_image_processed',
            error: error.message
        });
    }
}

/**
 * Decode base64 image data
 */
function decodeBase64Image(data) {
    const { base64Data } = data;
    
    try {
        const binaryString = atob(base64Data);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        
        self.postMessage({
            success: true,
            type: 'base64_decoded',
            data: {
                bytes: bytes,
                size: bytes.length
            }
        });
        
    } catch (error) {
        self.postMessage({
            success: false,
            type: 'base64_decoded',
            error: error.message
        });
    }
}

/**
 * Enhance image data for better quality
 */
function enhanceImage(data) {
    const { bytes, width, height, enhancementOptions = {} } = data;
    
    try {
        const enhancedBytes = new Uint8Array(bytes.length);
        
        // Apply contrast enhancement
        const contrast = enhancementOptions.contrast || 1.5;
        const brightness = enhancementOptions.brightness || 0.1;
        
        for (let i = 0; i < bytes.length; i++) {
            let pixel = bytes[i] & 0xFF;
            
            // Apply contrast and brightness
            pixel = Math.max(0, Math.min(255, (pixel - 128) * contrast + 128 + brightness * 255));
            
            enhancedBytes[i] = pixel;
        }
        
        self.postMessage({
            success: true,
            type: 'image_enhanced',
            data: {
                enhancedBytes: enhancedBytes,
                width: width,
                height: height
            }
        });
        
    } catch (error) {
        self.postMessage({
            success: false,
            type: 'image_enhanced',
            error: error.message
        });
    }
}

/**
 * Apply vertical flip to image data (critical for fingerprint display)
 */
function applyVerticalFlip(bytes, width, height) {
    // Flip image vertically by swapping rows
    for (let y = 0; y < height / 2; y++) {
        const swapY = height - y - 1;
        for (let x = 0; x < width; x++) {
            const index = y * width + x;
            const swapIndex = swapY * width + x;
            const temp = bytes[index];
            bytes[index] = bytes[swapIndex];
            bytes[swapIndex] = temp;
        }
    }
}

/**
 * Enhance image data for better fingerprint quality
 */
function enhanceImageData(bytes, width, height) {
    // Calculate histogram for adaptive enhancement
    const histogram = new Array(256).fill(0);
    for (let i = 0; i < bytes.length; i++) {
        histogram[bytes[i] & 0xFF]++;
    }
    
    // Find min and max values for contrast stretching
    let min = 0, max = 255;
    for (let i = 0; i < 256; i++) {
        if (histogram[i] > 0) {
            min = i;
            break;
        }
    }
    for (let i = 255; i >= 0; i--) {
        if (histogram[i] > 0) {
            max = i;
            break;
        }
    }
    
    // Apply contrast stretching and enhancement
    const contrast = 1.5;
    const brightness = 0.1;
    
    for (let i = 0; i < bytes.length; i++) {
        let pixel = bytes[i] & 0xFF;
        
        // Normalize to 0-1 range
        let normalized = (pixel - min) / (max - min);
        
        // Apply contrast and brightness
        normalized = Math.max(0, Math.min(1, normalized * contrast + brightness));
        
        // Convert back to byte
        bytes[i] = Math.round(normalized * 255);
    }
}

// Handle errors
self.onerror = function(error) {
    self.postMessage({
        success: false,
        error: `Worker error: ${error.message}`
    });
};
