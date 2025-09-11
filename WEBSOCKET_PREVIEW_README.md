# BIO600 Fingerprint WebSocket Real-Time Preview

This document describes the WebSocket-based real-time fingerprint preview functionality added to the BIO600 Fingerprint Service.

## Overview

The WebSocket preview system provides real-time streaming of fingerprint images from the BIO600 device, similar to the demo application but accessible via web browser. It supports multiple client connections and provides live preview at 15 FPS with quality scoring.

## Features

- **Real-time Preview**: Stream fingerprint images at 15 FPS
- **Quality Scoring**: Real-time quality assessment (0-100)
- **Multiple Clients**: Support for multiple simultaneous connections
- **Web Interface**: Modern HTML5 interface with canvas rendering
- **Device Integration**: Full integration with existing BIO600 SDK
- **Auto-reconnection**: Automatic WebSocket reconnection on disconnect

## WebSocket Endpoint

- **URL**: `ws://localhost:8080/ws/fingerprint`
- **Protocol**: WebSocket
- **Message Format**: JSON

## Message Types

### Client to Server

#### Start Preview
```json
{
  "command": "start_preview",
  "channel": 0,
  "width": 1600,
  "height": 1500
}
```

#### Stop Preview
```json
{
  "command": "stop_preview"
}
```

#### Capture Image
```json
{
  "command": "capture",
  "channel": 0,
  "width": 1600,
  "height": 1500
}
```

#### Get Status
```json
{
  "command": "get_status"
}
```

### Server to Client

#### Preview Frame
```json
{
  "type": "preview",
  "data": {
    "imageData": "base64_encoded_image_data",
    "quality": 85,
    "width": 1600,
    "height": 1500,
    "has_finger": true,
    "timestamp": 1234567890123
  },
  "timestamp": 1234567890123
}
```

#### Capture Result
```json
{
  "type": "capture_result",
  "data": {
    "success": true,
    "imageData": "base64_encoded_image_data",
    "quality": 92,
    "width": 1600,
    "height": 1500,
    "channel": 0,
    "storage_info": {
      "stored": true,
      "file_path": "/path/to/file.png",
      "filename": "fingerprint_123.png",
      "file_size": 245760
    }
  },
  "timestamp": 1234567890123
}
```

#### Connection Status
```json
{
  "type": "connection",
  "data": {
    "status": "connected",
    "session_id": "session_123",
    "message": "Connected to BIO600 Fingerprint WebSocket Service"
  },
  "timestamp": 1234567890123
}
```

#### Error Message
```json
{
  "type": "error",
  "data": {
    "message": "Error description",
    "timestamp": 1234567890123
  },
  "timestamp": 1234567890123
}
```

## Web Interface

Access the preview interface at: `http://localhost:8080/preview`

### Features
- **Real-time Canvas Preview**: Live fingerprint display
- **Quality Indicator**: Color-coded quality display
- **FPS Counter**: Real-time frame rate monitoring
- **Control Panel**: Start/Stop/Capture buttons
- **Device Settings**: Channel, width, height configuration
- **Connection Status**: WebSocket connection monitoring
- **System Log**: Real-time event logging

### Controls
- **Start Preview**: Begin real-time streaming
- **Stop Preview**: Stop streaming
- **Capture Image**: Capture high-quality image
- **Channel Selection**: Choose device channel (0-2)
- **Resolution Settings**: Configure image dimensions

## Technical Implementation

### Architecture
- **WebSocket Handler**: Manages client connections and message routing
- **Preview Service**: Handles device communication and image processing
- **Thread Management**: Separate threads for each preview stream
- **Frame Buffering**: Latest frame caching for multiple clients

### Performance
- **Target FPS**: 15 frames per second
- **Frame Timing**: 66ms intervals between frames
- **Quality Assessment**: Real-time using SDK quality functions
- **Memory Management**: Efficient frame processing and cleanup

### Device Integration
- **SDK Compatibility**: Full integration with existing BIO600 SDK
- **LED Control**: Visual feedback via device LEDs
- **Sound Feedback**: Audio cues for operations
- **Error Handling**: Comprehensive error management

## Usage Examples

### JavaScript Client
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/fingerprint');

ws.onopen = () => {
    // Start preview
    ws.send(JSON.stringify({
        command: 'start_preview',
        channel: 0,
        width: 1600,
        height: 1500
    }));
};

ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'preview') {
        // Display image data
        displayImage(message.data.imageData, message.data.quality);
    }
};
```

### Python Client
```python
import websocket
import json

def on_message(ws, message):
    data = json.loads(message)
    if data['type'] == 'preview':
        print(f"Quality: {data['data']['quality']}")

ws = websocket.WebSocketApp("ws://localhost:8080/ws/fingerprint",
                          on_message=on_message)
ws.run_forever()
```

## Configuration

### Application Properties
```properties
# Server Configuration
server.port=8080

# WebSocket Configuration
spring.websocket.max-text-message-buffer-size=1048576
spring.websocket.max-binary-message-buffer-size=1048576
```

### Device Settings
- **Default Channel**: 0
- **Default Resolution**: 1600x1500
- **Preview FPS**: 15
- **Quality Threshold**: 0-100

## Troubleshooting

### Common Issues

1. **WebSocket Connection Failed**
   - Check if service is running on port 8080
   - Verify firewall settings
   - Ensure device is connected

2. **No Preview Images**
   - Check device initialization
   - Verify channel settings
   - Check device LED indicators

3. **Poor Quality Scores**
   - Clean fingerprint sensor
   - Adjust finger placement
   - Check lighting conditions

4. **High CPU Usage**
   - Reduce preview FPS
   - Lower image resolution
   - Check for multiple preview streams

### Logs
Monitor application logs for detailed debugging information:
```bash
tail -f logs/application.log
```

## Development

### Building
```bash
mvn clean package
```

### Running
```bash
java -jar target/fingerprint-service-0.0.1-SNAPSHOT.jar
```

### Testing
1. Open browser to `http://localhost:8080/preview`
2. Click "Start Preview"
3. Place finger on sensor
4. Observe real-time preview and quality scores

## Integration

The WebSocket preview system integrates seamlessly with the existing REST API:
- **Shared Device Service**: Uses same device management
- **Consistent Quality Scoring**: Same algorithms as REST endpoints
- **Unified Storage**: Images saved to same storage system
- **Platform Compatibility**: Same Windows-only requirements

## Future Enhancements

- **Multiple Device Support**: Support for multiple BIO600 devices
- **Recording Functionality**: Save preview streams as videos
- **Advanced Filtering**: Image enhancement and filtering
- **Mobile Support**: Responsive design for mobile devices
- **Authentication**: Secure WebSocket connections
