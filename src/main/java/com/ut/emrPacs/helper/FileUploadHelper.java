package com.ut.emrPacs.helper;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * File upload helper (multipart and base64) used by the API.
 *
 * <p>Stores files under {@code ${catalina.base}/logs/EMRLogs/upload} by default and returns paths in DB-friendly form.</p>
 */
public class FileUploadHelper {

    private static final String ROOT;
    private static final String PROJECT_NAME;
    private static final String FOLDER_UPLOAD;

    private static final Logger LOGGER = Logger.getLogger(FileUploadHelper.class.getName());
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L; // 10MB default safety cap

    static {
        ROOT = System.getProperty("catalina.base", System.getProperty("java.io.tmpdir"));
        PROJECT_NAME = "logs/EMRLogs";
        FOLDER_UPLOAD = "upload";
    }

    private FileUploadHelper() {
        // Prevent instantiation
    }

    /* =======================
     * Upload (multipart)
     * ======================= */
    /**
     * Saves a {@link MultipartFile} to the upload directory and returns the stored path (DB format).
     *
     * @return stored path like {@code /upload/<uuid>.<ext>}, or {@code ""} when invalid/failure.
     */
    public static String saveFileUploaded(MultipartFile fileUploaded) {
        if (fileUploaded == null || fileUploaded.isEmpty()) {
            LOGGER.warning("Invalid file: File is null or empty.");
            return "";
        }

        try {
            if (fileUploaded.getSize() > MAX_UPLOAD_BYTES) {
                LOGGER.warning("File upload rejected: file too large (" + fileUploaded.getSize() + " bytes).");
                return "";
            }

            byte[] bytes = IOUtils.toByteArray(fileUploaded.getInputStream());

            File path = getUploadDirectory();
            if (!validateUploadDirectory(path)) {
                return "";
            }

            String originalFilename = fileUploaded.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                LOGGER.warning("File upload failed: Original filename is missing.");
                return "";
            }

            String extension = FilenameUtils.getExtension(originalFilename);
            if (!validateFileExtension(extension)) {
                LOGGER.warning("File upload failed: Invalid file extension.");
                return "";
            }
            if (!validateMagicBytes(extension, bytes)) {
                LOGGER.warning("File upload failed: Content does not match extension.");
                return "";
            }

            String uniqueFilename = generateUniqueFilename(extension);
            File file = new File(path, uniqueFilename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }

            LOGGER.info("File uploaded successfully: " + uniqueFilename);
            return makeFileUploadedUrl(uniqueFilename);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "File upload failed due to IOException: " + e.getMessage(), e);
            return "";
        }
    }

    /* =======================
     * Upload (Base64)
     * ======================= */
    /**
     * Saves a base64 image string (data URL style) to the upload directory and returns the stored path (DB format).
     *
     * <p>Legacy behavior: defaults extension to {@code png}.</p>
     *
     * @return stored path like {@code /upload/<uuid>.png}, or {@code ""} when invalid/failure.
     */
    public static String saveFileBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            LOGGER.warning("Invalid Base64 input: Input is null or empty.");
            return "";
        }

        try {
            String[] parts = base64.split(",");
            if (parts.length < 2) {
                LOGGER.warning("Invalid Base64 input: Missing data after the comma.");
                return "";
            }

            String extension = extensionFromDataUrl(parts[0]);
            if (!validateFileExtension(extension)) {
                LOGGER.warning("Base64 file upload failed: Invalid or unsupported image type.");
                return "";
            }

            byte[] bytes = Base64.getDecoder().decode(parts[1]);
            if (bytes.length > MAX_UPLOAD_BYTES) {
                LOGGER.warning("Base64 upload rejected: decoded payload too large (" + bytes.length + " bytes).");
                return "";
            }
            if (!validateMagicBytes(extension, bytes)) {
                LOGGER.warning("Base64 upload failed: Content does not match declared type.");
                return "";
            }
            File path = getUploadDirectory();
            if (!validateUploadDirectory(path)) {
                return "";
            }

            String uniqueFilename = generateUniqueFilename(extension);
            File file = new File(path, uniqueFilename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }

            LOGGER.info("Base64 file uploaded successfully: " + uniqueFilename);
            return makeFileUploadedUrl(uniqueFilename);

        } catch (IllegalArgumentException | IOException e) {
            LOGGER.log(Level.SEVERE, "Base64 file upload failed: " + e.getMessage(), e);
            return "";
        }
    }

    /* =======================
     * URL builder
     * ======================= */
    /**
     * Builds a DB-stored upload path for the given filename.
     */
    public static String makeFileUploadedUrl(String filename) {
        if (filename == null || filename.isEmpty()) {
            LOGGER.warning("Cannot create file URL: Filename is null or empty.");
            return "";
        }
        return "/" + FOLDER_UPLOAD + "/" + filename; // matches DB format
    }

    /* =======================
     * Delete (safe helpers)
     * ======================= */
    private static final String SAFE_FILENAME_REGEX = "^[A-Za-z0-9._-]+$";

    /**
     * Extracts just "abc.jpg" from "/upload/abc.jpg" or "upload/abc.jpg".
     * Returns "" if invalid or unsafe.
     */
    public static String extractFilename(String pathOrUrl) {
        if (pathOrUrl == null) return "";
        String p = pathOrUrl.trim();

        // normalize
        if (p.startsWith("/")) p = p.substring(1);       // "upload/abc.jpg"
        if (p.startsWith("upload/")) p = p.substring(7); // "abc.jpg"

        // no nested paths
        if (p.contains("/") || p.contains("\\")) return "";

        // safe chars only
        if (!p.matches(SAFE_FILENAME_REGEX)) return "";

        // prevent path traversal / special directory entries
        if (p.startsWith(".") || p.equals(".") || p.equals("..") || p.contains("..")) return "";

        return p;
    }

    /**
     * Delete by filename only (under .../logs/EMRLogs/upload).
     * Returns true if deleted, false otherwise.
     */
    public static boolean deleteUploadedFile(String filename) {
        if (filename == null || filename.isEmpty() || !filename.matches(SAFE_FILENAME_REGEX)) {
            LOGGER.warning("Invalid filename for deletion.");
            return false;
        }
        File dir = getUploadDirectory();
        File file = new File(dir, filename);
        if (!file.exists()) {
            LOGGER.warning("File not found for deletion: " + filename);
            return false;
        }
        boolean ok = file.delete();
        if (ok) {
            LOGGER.info("File deleted: " + filename);
        } else {
            LOGGER.warning("Failed to delete: " + filename);
        }
        return ok;
    }

    /**
     * Convenience: accepts DB value like "/upload/xxx.jpg".
     */
    public static boolean deleteFromStoredPath(String storedPath) {
        String filename = extractFilename(storedPath);
        return !filename.isEmpty() && deleteUploadedFile(filename);
    }

    /**
     * Backward-compatible method name. Now delegates safely.
     * Accepts either filename or stored path.
     */
    public static void deleteFile(String fileName) {
        String filename = extractFilename(fileName);
        if (!filename.isEmpty()) {
            deleteUploadedFile(filename);
        } else {
            LOGGER.warning("deleteFile called with invalid path: " + fileName);
        }
    }

    /* =======================
     * Internal helpers
     * ======================= */
    private static String generateUniqueFilename(String extension) {
        return UUID.randomUUID().toString() + FilenameUtils.EXTENSION_SEPARATOR + extension;
    }

    private static boolean validateFileExtension(String extension) {
        if (extension == null || extension.isEmpty()) return false;
        // NOTE: Do not allow SVG uploads (XSS risk when served inline under same origin).
        String[] allowed = { "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff" };
        for (String a : allowed) if (a.equalsIgnoreCase(extension)) return true;
        return false;
    }

    private static String extensionFromDataUrl(String header) {
        if (header == null) return "png";
        String h = header.trim().toLowerCase(Locale.ROOT);
        if (!h.startsWith("data:") || !h.contains(";")) return "png";

        String mime = h.substring("data:".length(), h.indexOf(';')).trim();
        return switch (mime) {
            case "image/jpeg" -> "jpeg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "image/tiff" -> "tiff";
            default -> "";
        };
    }

    private static boolean validateMagicBytes(String extension, byte[] bytes) {
        if (extension == null || bytes == null) return false;
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        if (bytes.length < 4) return false;

        return switch (ext) {
            case "jpg", "jpeg" -> bytes.length >= 3
                    && (bytes[0] == (byte) 0xFF)
                    && (bytes[1] == (byte) 0xD8)
                    && (bytes[2] == (byte) 0xFF);
            case "png" -> bytes.length >= 8
                    && (bytes[0] == (byte) 0x89)
                    && (bytes[1] == (byte) 0x50)
                    && (bytes[2] == (byte) 0x4E)
                    && (bytes[3] == (byte) 0x47)
                    && (bytes[4] == (byte) 0x0D)
                    && (bytes[5] == (byte) 0x0A)
                    && (bytes[6] == (byte) 0x1A)
                    && (bytes[7] == (byte) 0x0A);
            case "gif" -> bytes.length >= 6
                    && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == '8'
                    && (bytes[4] == '7' || bytes[4] == '9')
                    && bytes[5] == 'a';
            case "bmp" -> bytes[0] == 'B' && bytes[1] == 'M';
            case "webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            case "tiff" -> bytes.length >= 4
                    && (
                    (bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 0x2A && bytes[3] == 0x00)
                            || (bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0x00 && bytes[3] == 0x2A)
            );
            default -> false;
        };
    }

    private static File getUploadDirectory() {
        return new File(ROOT + File.separator + PROJECT_NAME + File.separator + FOLDER_UPLOAD);
    }

    private static boolean validateUploadDirectory(File path) {
        if (path == null) {
            LOGGER.warning("Upload directory is null.");
            return false;
        }

        if (path.exists()) {
            if (!path.isDirectory()) {
                LOGGER.warning("Upload path exists but is not a directory.");
                return false;
            }
            return true;
        }

        if (!path.mkdirs()) {
            LOGGER.warning("Failed to create upload directory.");
            return false;
        }

        if (!path.isDirectory()) {
            LOGGER.warning("Upload directory creation failed (not a directory).");
            return false;
        }

        return true;
    }
}
