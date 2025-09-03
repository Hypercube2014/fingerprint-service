package com.hypercube.fingerprint_service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FingerprintFileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FingerprintFileStorageService.class);

    @Value("${fingerprint.storage.base-path:./fingerprints}")
    private String baseStoragePath;

    @Value("${fingerprint.storage.standard-path:standard}")
    private String standardPath;

    @Value("${fingerprint.storage.original-path:original}")
    private String originalPath;

    @Value("${fingerprint.storage.rolled-path:rolled}")
    private String rolledPath;

    @Value("${fingerprint.storage.split-path:split}")
    private String splitPath;

    @Value("${fingerprint.storage.file-extension:.png}")
    private String fileExtension;

    @Value("${fingerprint.storage.image-format:PNG}")
    private String imageFormat;

    @Value("${fingerprint.storage.create-timestamp:true}")
    private boolean createTimestamp;

    @Value("${fingerprint.storage.create-uuid:true}")
    private boolean createUuid;

    @Value("${fingerprint.storage.max-files-per-directory:1000}")
    private int maxFilesPerDirectory;

    /**
     * Store fingerprint image with automatic path and naming
     */
    public FileStorageResult storeFingerprintImage(byte[] imageData, String imageType, String customName) {
        try {
            // Determine storage path based on image type
            String storagePath = getStoragePathForType(imageType);

            // Create full directory path
            Path fullPath = Paths.get(baseStoragePath, storagePath);
            Files.createDirectories(fullPath);

            // Generate unique filename
            String filename = generateUniqueFilename(imageType, customName);
            Path filePath = fullPath.resolve(filename);

            // Write file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(imageData);
                fos.flush();
            }

            logger.info("Fingerprint image stored successfully: {}", filePath);

            return new FileStorageResult(
                    true,
                    "Image stored successfully",
                    filePath.toString(),
                    filename,
                    imageData.length,
                    imageType
            );

        } catch (IOException e) {
            logger.error("Error storing fingerprint image", e);
            return new FileStorageResult(
                    false,
                    "Error storing image: " + e.getMessage(),
                    null,
                    null,
                    0,
                    imageType
            );
        }
    }

    /**
     * Store fingerprint image with custom path and naming
     */
    public FileStorageResult storeFingerprintImageCustom(byte[] imageData, String customPath, String customName) {
        try {
            // Create full directory path
            Path fullPath = Paths.get(baseStoragePath, customPath);
            Files.createDirectories(fullPath);

            // Generate filename
            String filename = generateCustomFilename(customName);
            Path filePath = fullPath.resolve(filename);

            // Write file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(imageData);
                fos.flush();
            }

            logger.info("Fingerprint image stored successfully with custom path: {}", filePath);

            return new FileStorageResult(
                    true,
                    "Image stored successfully",
                    filePath.toString(),
                    filename,
                    imageData.length,
                    "custom"
            );

        } catch (IOException e) {
            logger.error("Error storing fingerprint image with custom path", e);
            return new FileStorageResult(
                    false,
                    "Error storing image: " + e.getMessage(),
                    null,
                    null,
                    0,
                    "custom"
            );
        }
    }

    /**
     * Get storage path for specific image type
     */
    private String getStoragePathForType(String imageType) {
        switch (imageType.toLowerCase()) {
            case "standard":
                return standardPath;
            case "original":
                return originalPath;
            case "rolled":
                return rolledPath;
            case "split":
                return splitPath;
            default:
                return standardPath;
        }
    }

    /**
     * Generate unique filename with timestamp and UUID
     */
    private String generateUniqueFilename(String imageType, String customName) {
        StringBuilder filename = new StringBuilder();

        // Add custom name if provided
        if (customName != null && !customName.trim().isEmpty()) {
            filename.append(customName.trim()).append("_");
        }

        // Add image type
        filename.append(imageType.toLowerCase()).append("_");

        // Add timestamp if enabled
        if (createTimestamp) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            filename.append(timestamp).append("_");
        }

        // Add UUID if enabled
        if (createUuid) {
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            filename.append(uuid).append("_");
        }

        // Add file extension
        filename.append(System.currentTimeMillis()).append(fileExtension);

        return filename.toString();
    }

    /**
     * Generate custom filename
     */
    private String generateCustomFilename(String customName) {
        StringBuilder filename = new StringBuilder();

        if (customName != null && !customName.trim().isEmpty()) {
            filename.append(customName.trim());
        } else {
            filename.append("fingerprint");
        }

        // Add timestamp if enabled
        if (createTimestamp) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            filename.append("_").append(timestamp);
        }

        // Add file extension
        filename.append(fileExtension);

        return filename.toString();
    }

    /**
     * Create organized directory structure with date-based subdirectories
     */
    public FileStorageResult storeFingerprintImageOrganized(byte[] imageData, String imageType, String customName) {
        try {
            // Create date-based directory structure
            LocalDateTime now = LocalDateTime.now();
            String yearMonth = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
            String day = now.format(DateTimeFormatter.ofPattern("dd"));

            // Determine storage path based on image type
            String storagePath = getStoragePathForType(imageType);

            // Create full directory path with date structure
            Path fullPath = Paths.get(baseStoragePath, storagePath, yearMonth, day);
            Files.createDirectories(fullPath);

            // Generate unique filename
            String filename = generateUniqueFilename(imageType, customName);
            Path filePath = fullPath.resolve(filename);

            // Write file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(imageData);
                fos.flush();
            }

            logger.info("Fingerprint image stored in organized structure: {}", filePath);

            return new FileStorageResult(
                    true,
                    "Image stored successfully in organized structure",
                    filePath.toString(),
                    filename,
                    imageData.length,
                    imageType
            );

        } catch (IOException e) {
            logger.error("Error storing fingerprint image in organized structure", e);
            return new FileStorageResult(
                    false,
                    "Error storing image: " + e.getMessage(),
                    null,
                    null,
                    0,
                    imageType
            );
        }
    }

    /**
     * Store fingerprint image as normal image file (PNG/JPEG) with automatic path and naming
     */
    public FileStorageResult storeFingerprintImageAsImage(byte[] imageData, String imageType, String customName, int width, int height) {
        try {
            // Determine storage path based on image type
            String storagePath = getStoragePathForType(imageType);

            // Create full directory path
            Path fullPath = Paths.get(baseStoragePath, storagePath);
            Files.createDirectories(fullPath);

            // Generate unique filename
            String filename = generateUniqueFilename(imageType, customName);
            Path filePath = fullPath.resolve(filename);

            // Convert raw byte data to BufferedImage
            BufferedImage bufferedImage = convertRawDataToImage(imageData, width, height);

            // Save as image file
            boolean saved = ImageIO.write(bufferedImage, imageFormat, filePath.toFile());

            if (!saved) {
                throw new IOException("Failed to write image file");
            }

            logger.info("Fingerprint image stored as {} successfully: {}", imageFormat, filePath);

            return new FileStorageResult(
                    true,
                    "Image stored successfully as " + imageFormat,
                    filePath.toString(),
                    filename,
                    filePath.toFile().length(),
                    imageType
            );

        } catch (IOException e) {
            logger.error("Error storing fingerprint image as {}", imageFormat, e);
            return new FileStorageResult(
                    false,
                    "Error storing image as " + imageFormat + ": " + e.getMessage(),
                    null,
                    null,
                    0,
                    imageType
            );
        }
    }

    /**
     * Store fingerprint image as normal image file in organized directory structure
     */
    public FileStorageResult storeFingerprintImageAsImageOrganized(byte[] imageData, String imageType, String customName, int width, int height) {
        try {
            // Create date-based directory structure
            LocalDateTime now = LocalDateTime.now();
            String yearMonth = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
            String day = now.format(DateTimeFormatter.ofPattern("dd"));

            // Determine storage path based on image type
            String storagePath = getStoragePathForType(imageType);

            // Create full directory path with date structure
            Path fullPath = Paths.get(baseStoragePath, storagePath, yearMonth, day);
            Files.createDirectories(fullPath);

            // Generate unique filename
            String filename = generateUniqueFilename(imageType, customName);
            Path filePath = fullPath.resolve(filename);

            // Convert raw byte data to BufferedImage
            BufferedImage bufferedImage = convertRawDataToImage(imageData, width, height);

            // Save as image file
            boolean saved = ImageIO.write(bufferedImage, imageFormat, filePath.toFile());

            if (!saved) {
                throw new IOException("Failed to write image file");
            }

            logger.info("Fingerprint image stored as {} in organized structure: {}", imageFormat, filePath);

            return new FileStorageResult(
                    true,
                    "Image stored successfully as " + imageFormat + " in organized structure",
                    filePath.toString(),
                    filename,
                    filePath.toFile().length(),
                    imageType
            );

        } catch (IOException e) {
            logger.error("Error storing fingerprint image as {} in organized structure", imageFormat, e);
            return new FileStorageResult(
                    false,
                    "Error storing image as " + imageFormat + " in organized structure: " + e.getMessage(),
                    null,
                    null,
                    0,
                    imageType
            );
        }
    }

    /**
     * Convert raw byte data to BufferedImage
     */
    private BufferedImage convertRawDataToImage(byte[] imageData, int width, int height) {
        // Create a grayscale BufferedImage
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Get the raster data buffer
        DataBufferByte dataBuffer = (DataBufferByte) bufferedImage.getRaster().getDataBuffer();
        byte[] data = dataBuffer.getData();

        // Copy the raw data to the image buffer
        System.arraycopy(imageData, 0, data, 0, Math.min(imageData.length, data.length));

        return bufferedImage;
    }

    /**
     * Get file information
     */
    public FileInfo getFileInfo(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                return new FileInfo(
                        path.toString(),
                        path.getFileName().toString(),
                        Files.size(path),
                        Files.getLastModifiedTime(path).toInstant(),
                        Files.probeContentType(path)
                );
            }
            return null;
        } catch (IOException e) {
            logger.error("Error getting file info for: {}", filePath, e);
            return null;
        }
    }

    /**
     * Delete fingerprint image
     */
    public boolean deleteFingerprintImage(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                logger.info("Fingerprint image deleted: {}", filePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.error("Error deleting fingerprint image: {}", filePath, e);
            return false;
        }
    }

    /**
     * Get storage statistics
     */
    public StorageStats getStorageStats() {
        try {
            Path basePath = Paths.get(baseStoragePath);
            if (!Files.exists(basePath)) {
                return new StorageStats(0, 0, 0);
            }

            long totalFiles = Files.walk(basePath)
                    .filter(Files::isRegularFile)
                    .count();

            long totalSize = Files.walk(basePath)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();

            long directoryCount = Files.walk(basePath)
                    .filter(Files::isDirectory)
                    .count();

            return new StorageStats(totalFiles, totalSize, directoryCount);

        } catch (IOException e) {
            logger.error("Error getting storage statistics", e);
            return new StorageStats(0, 0, 0);
        }
    }

    /**
     * Clean up old files based on retention policy
     */
    public CleanupResult cleanupOldFiles(int daysToKeep) {
        try {
            Path basePath = Paths.get(baseStoragePath);
            if (!Files.exists(basePath)) {
                return new CleanupResult(0, 0, "No storage directory found");
            }

            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
            final int[] deletedFiles = {0};
            final long[] freedSpace = {0L};

            Files.walk(basePath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoffDate.toInstant(java.time.ZoneOffset.UTC))) {
                                long fileSize = Files.size(path);
                                Files.delete(path);
                                deletedFiles[0]++;
                                freedSpace[0] += fileSize;
                                logger.info("Deleted old file: {}", path);
                            }
                        } catch (IOException e) {
                            logger.warn("Could not process file for cleanup: {}", path, e);
                        }
                    });

            return new CleanupResult(deletedFiles[0], freedSpace[0], "Cleanup completed successfully");

        } catch (IOException e) {
            logger.error("Error during cleanup", e);
            return new CleanupResult(0, 0, "Error during cleanup: " + e.getMessage());
        }
    }

    // Result classes
    public static class FileStorageResult {
        private final boolean success;
        private final String message;
        private final String filePath;
        private final String filename;
        private final long fileSize;
        private final String imageType;

        public FileStorageResult(boolean success, String message, String filePath, String filename, long fileSize, String imageType) {
            this.success = success;
            this.message = message;
            this.filePath = filePath;
            this.filename = filename;
            this.fileSize = fileSize;
            this.imageType = imageType;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getFilePath() { return filePath; }
        public String getFilename() { return filename; }
        public long getFileSize() { return fileSize; }
        public String getImageType() { return imageType; }
    }

    public static class FileInfo {
        private final String filePath;
        private final String filename;
        private final long size;
        private final java.time.Instant lastModified;
        private final String contentType;

        public FileInfo(String filePath, String filename, long size, java.time.Instant lastModified, String contentType) {
            this.filePath = filePath;
            this.filename = filename;
            this.size = size;
            this.lastModified = lastModified;
            this.contentType = contentType;
        }

        // Getters
        public String getFilePath() { return filePath; }
        public String getFilename() { return filename; }
        public long getSize() { return size; }
        public java.time.Instant getLastModified() { return lastModified; }
        public String getContentType() { return contentType; }
    }

    public static class StorageStats {
        private final long totalFiles;
        private final long totalSize;
        private final long directoryCount;

        public StorageStats(long totalFiles, long totalSize, long directoryCount) {
            this.totalFiles = totalFiles;
            this.totalSize = totalSize;
            this.directoryCount = directoryCount;
        }

        // Getters
        public long getTotalFiles() { return totalFiles; }
        public long getTotalSize() { return totalSize; }
        public long getDirectoryCount() { return directoryCount; }
    }

    public static class CleanupResult {
        private final int deletedFiles;
        private final long freedSpace;
        private final String message;

        public CleanupResult(int deletedFiles, long freedSpace, String message) {
            this.deletedFiles = deletedFiles;
            this.freedSpace = freedSpace;
            this.message = message;
        }

        // Getters
        public int getDeletedFiles() { return deletedFiles; }
        public long getFreedSpace() { return freedSpace; }
        public String getMessage() { return message; }
    }
}
