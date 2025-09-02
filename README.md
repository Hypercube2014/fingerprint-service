# BIO600 Fingerprint Service

A clean, professional Spring Boot microservice for integrating with BIO600 fingerprint scanner devices. This service provides REST APIs for fingerprint capture, processing, device management, and **advanced file storage with configurable paths and unique naming conventions**.

## Features

- **Device Management**: Initialize, configure, and manage fingerprint scanner devices
- **Fingerprint Capture**: Capture standard, high-resolution, and rolled fingerprints
- **Image Processing**: Split multiple fingers from images and assess quality
- **Advanced File Storage**: Configurable storage paths with unique naming conventions
- **REST API**: Clean, documented REST endpoints for easy integration
- **Multi-channel Support**: Support for multiple scanner channels
- **Quality Assessment**: Built-in fingerprint quality evaluation
- **Error Handling**: Comprehensive error handling and logging
- **File Management**: Store, retrieve, and manage fingerprint files with metadata

## System Requirements

- **Operating System**: Windows (due to DLL dependencies)
- **Java**: JDK 17 or higher
- **Dependencies**: JNA library (included in pom.xml)
- **Hardware**: BIO600 fingerprint scanner device

## Quick Start

### 1. Build the Service

```bash
cd fingerprint-service
mvn clean package
```

### 2. Run the Service

```bash
java -jar target/fingerprint-service-0.0.1-SNAPSHOT.jar
```

The service will start on port 8080 by default.

### 3. Test the Service

```bash
# Health check
curl http://localhost:8080/api/fingerprint/health

# Initialize device
curl -X POST "http://localhost:8080/api/fingerprint/init?channel=0"

# Capture fingerprint with custom name
curl -X POST "http://localhost:8080/api/fingerprint/capture?channel=0&width=1600&height=1500&customName=john_doe_right_index"
```

## File Storage Features

### Configurable Storage Paths

The service automatically organizes fingerprint images into configurable directory structures:

```
./fingerprints/
├── standard/          # Standard fingerprint captures
├── original/          # High-resolution original images
├── rolled/            # Rolled/stitched fingerprints
└── split/             # Split fingerprint images
```

### Unique Naming Conventions

Fingerprint files are automatically named using a configurable pattern:

**Default Pattern**: `{customName}_{imageType}_{timestamp}_{uuid}_{millis}.raw`

**Examples**:
- `john_doe_right_index_standard_20241215_143022_a1b2c3d4_1702650622123.raw`
- `jane_smith_left_thumb_original_20241215_143045_e5f6g7h8_1702650645123.raw`
- `user123_rolled_20241215_143100_i9j0k1l2_1702650660123.raw`

### Organized Directory Structure

Enable date-based organization for better file management:

```
./fingerprints/standard/2024/12/15/
├── john_doe_right_index_standard_20241215_143022_a1b2c3d4_1702650622123.raw
├── jane_smith_left_thumb_standard_20241215_143045_e5f6g7h8_1702650645123.raw
└── user456_right_middle_standard_20241215_143100_i9j0k1l2_1702650660123.raw
```

## API Endpoints

### Device Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/fingerprint/init` | Initialize fingerprint device |
| GET | `/api/fingerprint/device/info` | Get device information |
| POST | `/api/fingerprint/device/settings` | Set device parameters |
| GET | `/api/fingerprint/device/status` | Get device status |
| POST | `/api/fingerprint/close` | Close device |

### Fingerprint Capture (with File Storage)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/fingerprint/capture` | Capture standard fingerprint with custom naming |
| POST | `/api/fingerprint/capture/original` | Capture high-resolution image with custom naming |
| POST | `/api/fingerprint/capture/rolled` | Capture rolled fingerprint with custom naming |

### File Storage Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/fingerprint/storage/store` | Store fingerprint image with custom path/naming |
| POST | `/api/fingerprint/storage/store/organized` | Store in organized date-based structure |
| GET | `/api/fingerprint/storage/file/info` | Get file information |
| DELETE | `/api/fingerprint/storage/file/delete` | Delete fingerprint image |
| GET | `/api/fingerprint/storage/stats` | Get storage statistics |
| POST | `/api/fingerprint/storage/cleanup` | Clean up old files |

### Image Processing

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/fingerprint/split` | Split multiple fingers from image with custom naming |

### System

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/fingerprint/health` | Health check |

## API Usage Examples

### Capture Fingerprint with Custom Naming

```bash
curl -X POST "http://localhost:8080/api/fingerprint/capture?channel=0&width=1600&height=1500&customName=john_doe_right_index"
```

**Response:**
```json
{
  "success": true,
  "message": "Fingerprint captured successfully",
  "image": "base64_encoded_image_data",
  "width": 1600,
  "height": 1500,
  "quality_score": 85,
  "channel": 0,
  "captured_at": "2024-12-15T14:30:22.123Z",
  "file_info": {
    "file_path": "./fingerprints/standard/john_doe_right_index_standard_20241215_143022_a1b2c3d4_1702650622123.raw",
    "filename": "john_doe_right_index_standard_20241215_143022_a1b2c3d4_1702650622123.raw",
    "file_size": 2400000,
    "image_type": "standard"
  }
}
```

### Store Image with Custom Path

```bash
curl -X POST "http://localhost:8080/api/fingerprint/storage/store" \
  -d "image=base64_encoded_image" \
  -d "image_type=standard" \
  -d "customName=special_case_001" \
  -d "customPath=custom/special_cases"
```

**Response:**
```json
{
  "success": true,
  "message": "Image stored successfully",
  "file_path": "./fingerprints/custom/special_cases/special_case_001_20241215_143022.raw",
  "filename": "special_case_001_20241215_143022.raw",
  "file_size": 2400000,
  "image_type": "custom",
  "timestamp": "2024-12-15T14:30:22.123Z"
}
```

### Store in Organized Structure

```bash
curl -X POST "http://localhost:8080/api/fingerprint/storage/store/organized" \
  -d "image=base64_encoded_image" \
  -d "image_type=standard" \
  -d "customName=john_doe_right_index"
```

**Response:**
```json
{
  "success": true,
  "message": "Image stored successfully in organized structure",
  "file_path": "./fingerprints/standard/2024/12/15/john_doe_right_index_standard_20241215_143022_a1b2c3d4_1702650622123.raw",
  "filename": "john_doe_right_index_standard_20241215_143022_a1b2c3d4_1702650622123.raw",
  "file_size": 2400000,
  "image_type": "standard",
  "timestamp": "2024-12-15T14:30:22.123Z"
}
```

### Get Storage Statistics

```bash
curl "http://localhost:8080/api/fingerprint/storage/stats"
```

**Response:**
```json
{
  "success": true,
  "total_files": 150,
  "total_size": 360000000,
  "directory_count": 25,
  "timestamp": "2024-12-15T14:30:22.123Z"
}
```

### Clean Up Old Files

```bash
curl -X POST "http://localhost:8080/api/fingerprint/storage/cleanup?daysToKeep=30"
```

**Response:**
```json
{
  "success": true,
  "deleted_files": 45,
  "freed_space": 108000000,
  "message": "Cleanup completed successfully",
  "timestamp": "2024-12-15T14:30:22.123Z"
}
```

## Configuration

The service can be configured through `application.properties`:

```properties
# Device Configuration
fingerprint.device.default-channel=0
fingerprint.device.default-width=1600
fingerprint.device.default-height=1500
fingerprint.device.quality-threshold=70

# Image Configuration
fingerprint.image.max-size=2688x1944
fingerprint.image.standard-size=1600x1500
fingerprint.image.rolled-size=800x750
fingerprint.image.split-size=300x400

# Service Configuration
fingerprint.service.timeout=30000
fingerprint.service.max-retries=3

# Storage Configuration
fingerprint.storage.base-path=./fingerprints
fingerprint.storage.standard-path=standard
fingerprint.storage.original-path=original
fingerprint.storage.rolled-path=rolled
fingerprint.storage.split-path=split
fingerprint.storage.file-extension=.raw
fingerprint.storage.create-timestamp=true
fingerprint.storage.create-uuid=true
fingerprint.storage.max-files-per-directory=1000
fingerprint.storage.retention-days=90
fingerprint.storage.organized-structure=true
```

### Storage Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `fingerprint.storage.base-path` | `./fingerprints` | Base directory for all fingerprint storage |
| `fingerprint.storage.standard-path` | `standard` | Subdirectory for standard fingerprints |
| `fingerprint.storage.original-path` | `original` | Subdirectory for original high-res images |
| `fingerprint.storage.rolled-path` | `rolled` | Subdirectory for rolled fingerprints |
| `fingerprint.storage.split-path` | `split` | Subdirectory for split fingerprint images |
| `fingerprint.storage.file-extension` | `.raw` | File extension for stored images |
| `fingerprint.storage.create-timestamp` | `true` | Include timestamp in filename |
| `fingerprint.storage.create-uuid` | `true` | Include UUID in filename |
| `fingerprint.storage.max-files-per-directory` | `1000` | Maximum files per directory |
| `fingerprint.storage.retention-days` | `90` | Days to keep files before cleanup |
| `fingerprint.storage.organized-structure` | `true` | Enable date-based organization |

## Integration with Laravel

### 1. Install HTTP Client

```bash
composer require guzzlehttp/guzzle
```

### 2. Create Service Class

```php
<?php

namespace App\Services;

use GuzzleHttp\Client;
use GuzzleHttp\Exception\GuzzleException;

class FingerprintService
{
    private $client;
    private $baseUrl;

    public function __construct()
    {
        $this->client = new Client();
        $this->baseUrl = config('fingerprint.service_url', 'http://localhost:8080');
    }

    public function captureFingerprintWithCustomName(int $channel = 0, int $width = 1600, int $height = 1500, string $customName = null): array
    {
        try {
            $params = [
                'channel' => $channel,
                'width' => $width,
                'height' => $height
            ];
            
            if ($customName) {
                $params['customName'] = $customName;
            }

            $response = $this->client->post($this->baseUrl . '/api/fingerprint/capture', [
                'query' => $params
            ]);

            $data = json_decode($response->getBody(), true);
            
            if ($data['success'] && isset($data['file_info'])) {
                // Store file path in database for later reference
                $this->storeFingerprintRecord($data['file_info'], $customName);
            }

            return $data;
        } catch (GuzzleException $e) {
            return [
                'success' => false,
                'message' => 'Failed to capture fingerprint: ' . $e->getMessage()
            ];
        }
    }

    public function storeFingerprintImage(string $base64Image, string $imageType, string $customName = null, string $customPath = null): array
    {
        try {
            $params = [
                'image' => $base64Image,
                'image_type' => $imageType
            ];
            
            if ($customName) {
                $params['customName'] = $customName;
            }
            
            if ($customPath) {
                $params['customPath'] = $customPath;
            }

            $response = $this->client->post($this->baseUrl . '/api/fingerprint/storage/store', [
                'form_params' => $params
            ]);

            return json_decode($response->getBody(), true);
        } catch (GuzzleException $e) {
            return [
                'success' => false,
                'message' => 'Failed to store fingerprint image: ' . $e->getMessage()
            ];
        }
    }

    private function storeFingerprintRecord(array $fileInfo, string $customName = null): void
    {
        // Store fingerprint record in database
        \App\Models\Fingerprint::create([
            'user_id' => auth()->id(),
            'file_path' => $fileInfo['file_path'],
            'filename' => $fileInfo['filename'],
            'file_size' => $fileInfo['file_size'],
            'image_type' => $fileInfo['image_type'],
            'custom_name' => $customName,
            'captured_at' => now()
        ]);
    }
}
```

### 3. Use in Controller

```php
<?php

namespace App\Http\Controllers;

use App\Services\FingerprintService;
use Illuminate\Http\Request;

class FingerprintController extends Controller
{
    private $fingerprintService;

    public function __construct(FingerprintService $fingerprintService)
    {
        $this->fingerprintService = $fingerprintService;
    }

    public function capture(Request $request)
    {
        $request->validate([
            'finger_type' => 'required|string',
            'hand' => 'required|in:left,right',
            'custom_name' => 'nullable|string|max:100'
        ]);

        $customName = $request->get('custom_name') ?: 
            auth()->user()->name . '_' . $request->hand . '_' . $request->finger_type;

        $result = $this->fingerprintService->captureFingerprintWithCustomName(
            0, 1600, 1500, $customName
        );
        
        if ($result['success']) {
            return response()->json([
                'success' => true,
                'message' => 'Fingerprint captured successfully',
                'file_info' => $result['file_info'],
                'quality_score' => $result['quality_score']
            ]);
        }
        
        return response()->json([
            'success' => false,
            'message' => $result['message']
        ], 500);
    }

    public function storeImage(Request $request)
    {
        $request->validate([
            'image' => 'required|string',
            'image_type' => 'required|string',
            'custom_name' => 'nullable|string|max:100',
            'custom_path' => 'nullable|string|max:200'
        ]);

        $result = $this->fingerprintService->storeFingerprintImage(
            $request->image,
            $request->image_type,
            $request->custom_name,
            $request->custom_path
        );

        if ($result['success']) {
            return response()->json([
                'success' => true,
                'message' => 'Image stored successfully',
                'file_info' => [
                    'file_path' => $result['file_path'],
                    'filename' => $result['filename'],
                    'file_size' => $result['file_size']
                ]
            ]);
        }

        return response()->json([
            'success' => false,
            'message' => $result['message']
        ], 500);
    }
}
```

## File Naming Patterns

### Standard Pattern
```
{customName}_{imageType}_{timestamp}_{uuid}_{millis}.{extension}
```

### Custom Pattern (when customPath is specified)
```
{customName}_{timestamp}.{extension}
```

### Organized Structure Pattern
```
{basePath}/{imageType}/{year}/{month}/{day}/{standardPattern}
```

## Error Handling

The service provides comprehensive error handling:

- **Device Errors**: Device initialization, connection, and configuration errors
- **Capture Errors**: Image capture and processing errors
- **Storage Errors**: File storage, path creation, and permission errors
- **Validation Errors**: Parameter validation and range checking
- **System Errors**: Service-level errors and exceptions

All errors include:
- Error message
- Timestamp
- Error context
- HTTP status codes

## Logging

The service uses SLF4J with configurable log levels:

- **INFO**: General service operations and file storage
- **DEBUG**: Detailed SDK operations and file operations
- **ERROR**: Error conditions and exceptions
- **WARN**: Warning conditions and storage issues

## Security Considerations

- **CORS**: Configured for cross-origin requests
- **Input Validation**: All parameters are validated
- **Error Sanitization**: Error messages are sanitized
- **Access Control**: Device access is controlled per channel
- **File Permissions**: Proper file permissions for stored images
- **Path Validation**: Prevents directory traversal attacks

## Troubleshooting

### Common Issues

1. **Device Not Found**
   - Ensure BIO600 device is connected
   - Check device drivers are installed
   - Verify DLL files are accessible

2. **Storage Permission Issues**
   - Check write permissions for storage directory
   - Verify disk space availability
   - Check file system permissions

3. **File Naming Conflicts**
   - Ensure custom names are unique
   - Check for special characters in names
   - Verify path length limits

4. **Initialization Failures**
   - Check device permissions
   - Verify device is not in use by another application
   - Check system resources

### Debug Steps

1. Check service logs for detailed error messages
2. Verify device connection and drivers
3. Test with simple operations first
4. Check system resources and permissions
5. Verify storage directory permissions and space

## Performance Optimization

1. **File Compression**: Consider compressing fingerprint images before storage
2. **Batch Operations**: Process multiple fingerprints in batches
3. **Async Storage**: Use async operations for non-critical file storage
4. **Cleanup Scheduling**: Schedule regular cleanup operations during off-peak hours

## Conclusion

This BIO600 fingerprint SDK provides robust fingerprint capture and processing capabilities with advanced file storage features. The recommended integration approach is through a Java microservice that exposes REST APIs, allowing Laravel to easily consume fingerprint services while maintaining security and performance.

The file storage system provides:
- **Flexible naming conventions** for easy identification
- **Organized directory structures** for better file management
- **Configurable storage paths** for different use cases
- **Automatic file management** with cleanup capabilities
- **Comprehensive metadata** for each stored file

For production use, ensure proper error handling, logging, and security measures are implemented. Consider implementing fingerprint template encryption and secure storage practices to protect sensitive biometric data.

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review service logs
3. Verify device compatibility
4. Check storage configuration
5. Contact development team

