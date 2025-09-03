# BIO600 Fingerprint Service

A Spring Boot service for capturing and processing fingerprint images using the BIO600 SDK.

## Features

- **Device Management**: Initialize, check status, and close fingerprint devices
- **Fingerprint Capture**: Capture fingerprint images with quality assessment
- **Automatic Image Storage**: Automatically store captured images as PNG files with organized directory structure
- **Platform Detection**: Automatic detection of Windows/Linux compatibility
- **REST API**: Clean REST endpoints for all operations

## API Endpoints

### Health & Platform Info
- `GET /api/fingerprint/health` - Service health check
- `GET /api/fingerprint/platform` - Platform compatibility information

### Device Management
- `POST /api/fingerprint/init?channel=0` - Initialize fingerprint device
- `GET /api/fingerprint/status` - Get device status for all channels
- `POST /api/fingerprint/close?channel=0` - Close fingerprint device

### Fingerprint Capture
- `POST /api/fingerprint/capture?channel=0&width=1600&height=1500` - Capture fingerprint image
- `GET /api/fingerprint/capture?channel=0&width=1600&height=1500` - Capture fingerprint image (GET method for testing)
- `POST /api/fingerprint/storage/test?channel=0&width=1600&height=1500&format=PNG` - Test image storage with different formats

### Fingerprint Splitting
- `POST /api/fingerprint/split/right-four?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400` - Split right four fingerprints (index, middle, ring, pinky)
- `POST /api/fingerprint/split/left-four?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400` - Split left four fingerprints (index, middle, ring, pinky)
- `POST /api/fingerprint/split/thumbs?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400` - Split two thumb fingerprints (left and right)
- `POST /api/fingerprint/split/single?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400&fingerPosition=0` - Split single fingerprint from specified position

### Storage Management
- `GET /api/fingerprint/storage/stats` - Get storage statistics
- `GET /api/fingerprint/storage/list?page=0&size=20` - List stored fingerprint images

## How the GET Capture Endpoint Works

### Purpose
The `GET /api/fingerprint/capture` endpoint is designed for **testing and development purposes**. It provides the same functionality as the POST endpoint but allows you to test fingerprint capture directly from a web browser or simple HTTP GET requests.

### How It Works
1. **Same Logic**: The GET endpoint internally calls the same capture logic as the POST endpoint
2. **Parameter Support**: Accepts the same parameters (channel, width, height) as query parameters
3. **Easy Testing**: You can test the endpoint directly in a browser by visiting the URL
4. **Same Response**: Returns identical response format as the POST endpoint

### Example Usage
```bash
# Test in browser - just visit this URL
http://localhost:8080/api/fingerprint/capture?channel=0&width=800&height=600

# Test with curl
curl "http://localhost:8080/api/fingerprint/capture?channel=0&width=800&height=600"

# Test with different dimensions
curl "http://localhost:8080/api/fingerprint/capture?channel=0&width=1600&height=1500"
```

### When to Use
- **Development Testing**: Quick testing during development
- **Browser Testing**: Easy testing from web browsers
- **Simple Integration**: When you need simple GET requests
- **Debugging**: Quick verification of capture functionality

### Production Use
For production applications, use the **POST endpoint** as it's more appropriate for operations that modify state (capturing fingerprints).

## Usage Examples

### 1. Check Platform Compatibility
```bash
curl http://localhost:8080/api/fingerprint/platform
```

### 2. Initialize Device
```bash
curl -X POST "http://localhost:8080/api/fingerprint/init?channel=0"
```

### 3. Capture Fingerprint (Automatically Stored as PNG)
```bash
# Using POST
curl -X POST "http://localhost:8080/api/fingerprint/capture?channel=0&width=1600&height=1500"

# Using GET (for testing)
curl "http://localhost:8080/api/fingerprint/capture?channel=0&width=1600&height=1500"
```

**Response**: The endpoint returns capture information and storage details, but **not the raw image data**. The image is automatically saved as a PNG file, and you can access it using the file path in the response.

### 4. Check Device Status
```bash
curl http://localhost:8080/api/fingerprint/status
```

### 5. Close Device
```bash
curl -X POST "http://localhost:8080/api/fingerprint/close?channel=0"
```

### 6. View Storage Statistics
```bash
curl http://localhost:8080/api/fingerprint/storage/stats
```

### 7. List Stored Images
```bash
curl "http://localhost:8080/api/fingerprint/storage/list?page=0&size=20"
```

### 8. Split Right Four Fingerprints
```bash
curl -X POST "http://localhost:8080/api/fingerprint/split/right-four?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400"
```

### 9. Split Left Four Fingerprints
```bash
curl -X POST "http://localhost:8080/api/fingerprint/split/left-four?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400"
```

### 10. Split Two Thumbs
```bash
curl -X POST "http://localhost:8080/api/fingerprint/split/thumbs?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400"
```

### 11. Split Single Finger
```bash
curl -X POST "http://localhost:8080/api/fingerprint/split/single?channel=0&width=1600&height=1500&splitWidth=300&splitHeight=400&fingerPosition=0"
```

## Response Format

### Successful Capture Response (with Storage Info)
```json
{
  "success": true,
  "message": "Fingerprint captured successfully",
  "width": 1600,
  "height": 1500,
  "quality_score": 85,
  "channel": 0,
  "captured_at": "2025-09-02T13:46:47.123Z",
  "storage_info": {
    "stored": true,
    "file_path": "./fingerprints/standard/2025/09/02/channel_0_1600x1500_standard_20250902_134647_abc12345_1735828007123.png",
    "filename": "channel_0_1600x1500_standard_20250902_134647_abc12345_1735828007123.png",
    "file_size": 2400000
  },
  "platform_info": "OS: Windows 10, Architecture: x64, Supported: true",
  "timestamp": 1735828007123
}
```

**Note**: The raw image data is not included in the response to reduce payload size. The image is automatically saved as a PNG file, and you can access it using the file path provided in `storage_info`.

### Successful Split Response
```json
{
  "success": true,
  "split_type": "right_four",
  "fingerprint_count": 4,
  "fingerprints": [
    {
      "finger_name": "right_index",
      "position": 0,
      "width": 300,
      "height": 400,
      "quality_score": 85,
      "storage_info": {
        "stored": true,
        "file_path": "./fingerprints/split/2025/09/02/right_index_0_split_20250902_134647_abc12345_1735828007123.png",
        "filename": "right_index_0_split_20250902_134647_abc12345_1735828007123.png",
        "file_size": 120000
      }
    },
    {
      "finger_name": "right_middle",
      "position": 1,
      "width": 300,
      "height": 400,
      "quality_score": 82,
      "storage_info": {
        "stored": true,
        "file_path": "./fingerprints/split/2025/09/02/right_middle_1_split_20250902_134647_def67890_1735828007123.png",
        "filename": "right_middle_1_split_20250902_134647_def67890_1735828007123.png",
        "file_size": 118000
      }
    }
  ],
  "split_width": 300,
  "split_height": 400,
  "original_width": 1600,
  "original_height": 1500,
  "channel": 0
}
```

### Error Response
```json
{
  "success": false,
  "message": "Platform not supported. This SDK requires Windows.",
  "platform_info": "OS: Linux, Architecture: x64, Supported: false",
  "channel": 0,
  "timestamp": 1735828007123
}
```

## Platform Requirements

⚠️ **Important**: This service requires Windows to run the BIO600 SDK. The service will automatically detect the platform and provide appropriate error messages on unsupported platforms.

### Supported Platforms
- ✅ Windows (with .dll files)

### Unsupported Platforms
- ❌ Linux (will show platform incompatibility message)
- ❌ macOS (will show platform incompatibility message)

## Configuration

The service uses default parameters that can be overridden:
- **Channel**: Default 0
- **Width**: Default 1600 pixels
- **Height**: Default 1500 pixels

## Storage Structure

The service automatically organizes stored fingerprint images in a hierarchical directory structure:

```
./fingerprints/
├── standard/           # Standard fingerprint images
│   ├── 2025/
│   │   ├── 09/
│   │   │   ├── 02/    # Date-based organization
│   │   │   │   ├── channel_0_1600x1500_standard_20250902_134647_abc12345_1735828007123.png
│   │   │   │   └── ... (more images)
│   │   │   └── ...
│   │   └── ...
│   └── ...
├── original/           # High-resolution original images
├── rolled/             # Rolled fingerprint images
└── split/              # Split fingerprint images
```

### File Naming Convention
- **Format**: `{customName}_{imageType}_{timestamp}_{uuid}_{timestamp}.png`
- **Example**: `channel_0_1600x1500_standard_20250902_134647_abc12345_1735828007123.png`

### Storage Configuration
```properties
fingerprint.storage.base-path=./fingerprints
fingerprint.storage.standard-path=standard
fingerprint.storage.original-path=original
fingerprint.storage.rolled-path=rolled
fingerprint.storage.split-path=split
fingerprint.storage.file-extension=.png
fingerprint.storage.image-format=PNG
fingerprint.storage.create-timestamp=true
fingerprint.storage.create-uuid=true
fingerprint.storage.max-files-per-directory=1000
```

## Quality Assessment

The service automatically assesses fingerprint quality using:
1. **SDK Quality Assessment** (if available)
2. **Fallback Algorithm** based on:
    - Image contrast (standard deviation)
    - Brightness levels
    - Fingerprint coverage area

## Image Storage & Conversion

### Image Format Conversion
The service automatically converts raw fingerprint data to standard image formats:
- **Input**: Raw byte data from fingerprint device
- **Output**: PNG image files (configurable to other formats)
- **Quality**: Grayscale images preserving fingerprint details
- **Size**: Maintains original dimensions (e.g., 1600x1500 pixels)

### Supported Image Formats
- **PNG** (default) - Lossless compression, best for fingerprint analysis
- **JPEG** - Configurable for smaller file sizes
- **BMP** - Uncompressed format for maximum quality

### Conversion Process
1. **Raw Data Capture**: Get raw byte data from fingerprint device
2. **Image Creation**: Convert to BufferedImage with proper dimensions
3. **Format Encoding**: Use ImageIO to save in desired format
4. **File Storage**: Save in organized directory structure

## Running the Service

```bash
# Navigate to the service directory
cd fingerprint-service

# Run with Maven
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/fingerprint-service-0.0.1-SNAPSHOT.jar
```

The service will start on port 8080 by default and create the storage directory structure automatically.

## Troubleshooting

### Error Code -106
This error typically occurs when:
- Running on an unsupported platform (Linux/macOS)
- DLL files are not accessible
- Device is not properly connected

### Platform Not Supported
If you see "Platform not supported" messages:
1. Ensure you're running on Windows
2. Check that all .dll files are in the service directory
3. Verify the BIO600 hardware is connected

### Storage Issues
If images are not being stored:
1. Check the `fingerprint.storage.base-path` configuration
2. Ensure the application has write permissions to the storage directory
3. Check the logs for storage-related errors

## Next Steps

This service provides the foundation for fingerprint capture and storage. Future enhancements could include:
- Fingerprint splitting (right/left four fingers, thumbs, single finger)
- Advanced quality assessment
- Batch processing capabilities
- Image compression and optimization
- Database integration for metadata storage

## Fingerprint Splitting

### What Are Split Fingerprints?

Fingerprint splitting is the process of taking a single captured fingerprint image and extracting multiple individual fingerprints from it. This is useful when you capture a hand with multiple fingers and want to process each finger separately.

### Split Types Explained

#### 1. **Right Four Fingerprints** (`/split/right-four`)
- **Purpose**: Extract 4 individual fingerprints from the right hand
- **Fingers**: Index, Middle, Ring, and Pinky fingers
- **Use Case**: Right hand fingerprint registration or verification
- **Output**: 4 separate PNG images, one for each finger

#### 2. **Left Four Fingerprints** (`/split/left-four`)
- **Purpose**: Extract 4 individual fingerprints from the left hand
- **Fingers**: Index, Middle, Ring, and Pinky fingers
- **Use Case**: Left hand fingerprint registration or verification
- **Output**: 4 separate PNG images, one for each finger

#### 3. **Two Thumbs** (`/split/thumbs`)
- **Purpose**: Extract both left and right thumb fingerprints
- **Fingers**: Left thumb and right thumb
- **Use Case**: Thumb-specific fingerprint processing
- **Output**: 2 separate PNG images, one for each thumb

#### 4. **Single Finger** (`/split/single`)
- **Purpose**: Extract a single fingerprint from a specific position
- **Parameters**: `fingerPosition` (0-9) to specify which finger
- **Use Case**: Individual finger processing or targeted extraction
- **Output**: 1 PNG image for the specified finger

### How Splitting Works

1. **Capture**: First captures a full fingerprint image
2. **Analysis**: Uses the FPSPLIT library to analyze the image
3. **Detection**: Identifies individual fingerprint regions
4. **Extraction**: Extracts each region into separate images
5. **Storage**: Saves each split image as a PNG file
6. **Metadata**: Provides position, quality, and storage information

### Finger Position Mapping

| Position | Finger Name | Description |
|----------|-------------|-------------|
| 0 | right_index | Right hand index finger |
| 1 | right_middle | Right hand middle finger |
| 2 | right_ring | Right hand ring finger |
| 3 | right_pinky | Right hand pinky finger |
| 4 | left_index | Left hand index finger |
| 5 | left_middle | Left hand middle finger |
| 6 | left_ring | Left hand ring finger |
| 7 | left_pinky | Left hand pinky finger |
| 8 | left_thumb | Left hand thumb |
| 9 | right_thumb | Right hand thumb |
