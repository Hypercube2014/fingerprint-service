# Coordinate Output Fix

## Problem
The fingerprint capture endpoint was generating excessive coordinate output in the logs, flooding the console with patterns like:
```
32 32
96 32
160 32
224 32
...
```

This was causing performance issues and making the logs unreadable.

## Root Cause
The coordinate output was coming from the native DLL libraries (GALSXXYY.dll, ZAZ_FpStdLib.dll, etc.) that have debug output enabled. These libraries print coordinate data during image processing operations.

## Solution
Implemented a comprehensive solution to suppress the coordinate output while preserving normal logging:

### 1. Output Stream Filtering
- Created a filtered output stream that intercepts `System.out` calls
- Filters out coordinate patterns (two numbers separated by space)
- Preserves all other output including normal application logs

### 2. Configuration Options
Added multiple ways to control debug output suppression:

#### Application Properties
```properties
# Set to false to enable native library debug output
fingerprint.suppress.debug=true
```

#### System Properties
```bash
# Disable suppression via system property
-Dfingerprint.suppress.debug=false
```

### 3. Targeted Suppression
The suppression is applied during specific operations:
- `captureFingerprint()` - During fingerprint capture
- `splitTwoThumbs()` - During thumb splitting
- `startPreviewStream()` - During preview operations

### 4. Automatic Restoration
Output streams are automatically restored after operations complete, ensuring normal logging continues to work.

## Implementation Details

### Files Modified
1. `FingerprintDeviceService.java` - Added output filtering logic
2. `application.properties` - Added configuration option

### Key Methods Added
- `redirectNativeOutput()` - Initial setup of output filtering
- `suppressNativeDebugOutput()` - Temporarily suppress during operations
- `restoreNativeDebugOutput()` - Restore normal output after operations

### Pattern Matching
The filter uses regex patterns to identify coordinate output:
- `^\\d+\\s+\\d+$` - Matches lines with two numbers separated by space
- `^\\d+\\s+\\d+\\s*$` - Matches lines with trailing whitespace

## Usage

### Default Behavior
By default, coordinate output is suppressed. The service will work normally without flooding the logs.

### Enable Debug Output
If you need to see the coordinate output for debugging:

1. **Via Application Properties:**
   ```properties
   fingerprint.suppress.debug=false
   ```

2. **Via System Property:**
   ```bash
   java -Dfingerprint.suppress.debug=false -jar fingerprint-service.jar
   ```

### Testing
The fix has been applied to all major fingerprint operations:
- `/api/fingerprint/capture` - Single fingerprint capture
- `/api/fingerprint/split/thumbs` - Two-thumb splitting
- `/api/fingerprint/preview/start` - Real-time preview

## Benefits
1. **Clean Logs** - No more coordinate flooding in console output
2. **Better Performance** - Reduced I/O overhead from excessive logging
3. **Configurable** - Can be enabled/disabled as needed
4. **Non-Breaking** - Preserves all normal application functionality
5. **Targeted** - Only suppresses during specific operations

## Verification
After applying this fix, the capture endpoint should work normally without generating the coordinate loop output. The logs will be clean and readable while maintaining all functionality.
