# Fingerprint Template Capture API

This document describes the new fingerprint template capture functionality added to the fingerprint service.

## Overview

The fingerprint service now supports capturing fingerprint templates in addition to raw images. Templates are compact binary representations of fingerprints that can be used for matching and verification without storing the original image data.

## Supported Template Formats

- **ISO/IEC 19794-2**: International standard format
- **ANSI/NIST-ITL 1-2000**: American National Standard format

## API Endpoints

### 1. Capture ISO Template

**Endpoint:** `POST /api/fingerprint/template/iso`

**Parameters:**
- `channel` (optional, default: 0): Device channel
- `width` (optional, default: 1600): Image width
- `height` (optional, default: 1500): Image height

**Response:**
```json
{
  "success": true,
  "message": "Fingerprint ISO template captured successfully",
  "template_format": "ISO",
  "template_size": 1024,
  "template_data": "base64_encoded_template_data",
  "quality_score": 85,
  "width": 1600,
  "height": 1500,
  "channel": 0,
  "captured_at": "2024-01-01T12:00:00Z",
  "platform_info": "OS: Windows 10, Architecture: amd64, Supported: true",
  "timestamp": 1704110400000
}
```

### 2. Capture ANSI Template

**Endpoint:** `POST /api/fingerprint/template/ansi`

**Parameters:**
- `channel` (optional, default: 0): Device channel
- `width` (optional, default: 1600): Image width
- `height` (optional, default: 1500): Image height

**Response:**
```json
{
  "success": true,
  "message": "Fingerprint ANSI template captured successfully",
  "template_format": "ANSI",
  "template_size": 1024,
  "template_data": "base64_encoded_template_data",
  "quality_score": 85,
  "width": 1600,
  "height": 1500,
  "channel": 0,
  "captured_at": "2024-01-01T12:00:00Z",
  "platform_info": "OS: Windows 10, Architecture: amd64, Supported: true",
  "timestamp": 1704110400000
}
```

### 3. Capture Both Templates

**Endpoint:** `POST /api/fingerprint/template/both`

**Parameters:**
- `channel` (optional, default: 0): Device channel
- `width` (optional, default: 1600): Image width
- `height` (optional, default: 1500): Image height

**Response:**
```json
{
  "success": true,
  "message": "Fingerprint templates captured successfully",
  "iso_template": {
    "format": "ISO",
    "template_data": "base64_encoded_iso_template",
    "template_size": 1024
  },
  "ansi_template": {
    "format": "ANSI",
    "template_data": "base64_encoded_ansi_template",
    "template_size": 1024
  },
  "quality_score": 85,
  "width": 1600,
  "height": 1500,
  "channel": 0,
  "captured_at": "2024-01-01T12:00:00Z",
  "platform_info": "OS: Windows 10, Architecture: amd64, Supported: true",
  "timestamp": 1704110400000
}
```

### 4. Compare Templates

**Endpoint:** `POST /api/fingerprint/template/compare`

**Parameters:**
- `template1`: Base64-encoded first template
- `template2`: Base64-encoded second template
- `channel` (optional, default: 0): Device channel

**Response:**
```json
{
  "success": true,
  "message": "Template comparison completed successfully",
  "similarity_score": 75,
  "match_threshold": 50,
  "is_match": true,
  "channel": 0,
  "compared_at": "2024-01-01T12:00:00Z",
  "platform_info": "OS: Windows 10, Architecture: amd64, Supported: true",
  "timestamp": 1704110400000
}
```

### 5. Search Templates

**Endpoint:** `POST /api/fingerprint/template/search`

**Parameters:**
- `search_template`: Base64-encoded template to search for
- `template_array`: Base64-encoded array of templates to search in
- `array_count`: Number of templates in the array
- `match_threshold` (optional, default: 50): Minimum similarity score for a match
- `channel` (optional, default: 0): Device channel

**Response:**
```json
{
  "success": true,
  "message": "Template search completed successfully",
  "search_result": 75,
  "best_match_score": 75,
  "match_found": true,
  "match_threshold": 50,
  "array_count": 10,
  "channel": 0,
  "searched_at": "2024-01-01T12:00:00Z",
  "platform_info": "OS: Windows 10, Architecture: amd64, Supported: true",
  "timestamp": 1704110400000
}
```

## Template Specifications

- **Size**: 1024 bytes (8192 bits)
- **Format**: Binary data encoded as Base64 in API responses
- **Quality**: Templates are only generated if image quality score is >= 10
- **Compatibility**: Works with ZAZ_FpStdLib SDK

## Usage Examples

### Capture a Template

```bash
curl -X POST "http://localhost:8080/api/fingerprint/template/iso" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "channel=0&width=1600&height=1500"
```

### Compare Two Templates

```bash
curl -X POST "http://localhost:8080/api/fingerprint/template/compare" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "template1=base64_template1&template2=base64_template2&channel=0"
```

## Error Handling

All endpoints return appropriate HTTP status codes and error messages:

- **400 Bad Request**: Platform not supported or invalid parameters
- **500 Internal Server Error**: Device initialization failed or template generation failed

Error responses include:
- `success`: false
- `message`: Human-readable error description
- `error_details`: Technical error details
- `platform_info`: Current platform information

## Platform Requirements

- **Operating System**: Windows (required for ZAZ_FpStdLib SDK)
- **Architecture**: x64 or x86
- **Dependencies**: ZAZ_FpStdLib.dll must be available

## Next Steps

This implementation provides the foundation for:
1. **Template Storage**: Store templates in a database for later retrieval
2. **Template Matching**: Use templates for identity verification
3. **Template Management**: CRUD operations for template collections
4. **Integration**: Connect with existing authentication systems

The templates can be used for:
- User registration and enrollment
- Identity verification
- Access control
- Biometric authentication systems
