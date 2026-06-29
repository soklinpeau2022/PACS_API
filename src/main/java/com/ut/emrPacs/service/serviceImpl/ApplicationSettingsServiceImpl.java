package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.dto.request.application.ApplicationBrandSettingsRequest;
import com.ut.emrPacs.model.dto.response.application.ApplicationBrandAssetResponse;
import com.ut.emrPacs.model.dto.response.application.ApplicationBrandSettingsResponse;
import com.ut.emrPacs.service.service.ApplicationSettingsService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.ut.emrPacs.helper.FunctionHelper.trimToNull;

@Service
public class ApplicationSettingsServiceImpl implements ApplicationSettingsService {
    private static final String SETTING_APP_NAME = "application.brand.app_name";
    private static final String SETTING_LOGO_URL = "application.brand.logo_url";
    private static final String SETTING_LOGIN_BACKGROUND_URL = "application.brand.login_background_url";

    private static final String DEFAULT_APP_NAME = "UDAYA_PACS_FRONTEND";
    private static final String DEFAULT_LOGO_URL = "/utemr-logo.png";
    private static final String DEFAULT_LOGIN_BACKGROUND_URL = "/banner-login.webp";
    private static final String DEFAULT_BRAND_ASSET_ROOT = "/home/Images/application-brand";
    private static final int MAX_APP_NAME_LENGTH = 80;
    private static final long MAX_BRAND_ASSET_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff");
    private static final String SAFE_FILENAME_REGEX = "^[A-Za-z0-9._-]+$";

    private final JdbcTemplate jdbcTemplate;
    private final File brandAssetDirectory;

    public ApplicationSettingsServiceImpl(
            JdbcTemplate jdbcTemplate,
            @Value("${pacs.application-brand.asset-root:/home/Images/application-brand}") String brandAssetRoot
    ) {
        this.jdbcTemplate = jdbcTemplate;
        String normalizedRoot = trimToNull(brandAssetRoot);
        this.brandAssetDirectory = new File(normalizedRoot == null ? DEFAULT_BRAND_ASSET_ROOT : normalizedRoot);
    }

    @Override
    public ApplicationBrandSettingsResponse getBrandSettings() {
        ApplicationBrandSettingsResponse response = new ApplicationBrandSettingsResponse();
        response.setAppName(readSetting(SETTING_APP_NAME, DEFAULT_APP_NAME));
        response.setLogoUrl(readSetting(SETTING_LOGO_URL, DEFAULT_LOGO_URL));
        response.setLoginBackgroundUrl(readSetting(SETTING_LOGIN_BACKGROUND_URL, DEFAULT_LOGIN_BACKGROUND_URL));
        return response;
    }

    @Override
    public ApplicationBrandSettingsResponse updateBrandSettings(ApplicationBrandSettingsRequest request, Long modifiedBy) {
        ApplicationBrandSettingsRequest safeRequest = request == null ? new ApplicationBrandSettingsRequest() : request;
        String appName = normalizeAppName(safeRequest.getAppName());
        String logoUrl = normalizeBrandUrl(safeRequest.getLogoUrl(), DEFAULT_LOGO_URL);
        String loginBackgroundUrl = normalizeBrandUrl(safeRequest.getLoginBackgroundUrl(), DEFAULT_LOGIN_BACKGROUND_URL);

        upsertSetting(SETTING_APP_NAME, appName, modifiedBy);
        upsertSetting(SETTING_LOGO_URL, logoUrl, modifiedBy);
        upsertSetting(SETTING_LOGIN_BACKGROUND_URL, loginBackgroundUrl, modifiedBy);
        return getBrandSettings();
    }

    @Override
    public ApplicationBrandAssetResponse uploadBrandAsset(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided or file is empty.");
        }
        if (file.getSize() > MAX_BRAND_ASSET_BYTES) {
            throw new IllegalArgumentException("Image can be up to 10 MB.");
        }
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        extension = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            extension = extensionFromContentType(file.getContentType());
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Upload a PNG, JPG, or WebP image.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException error) {
            throw new IllegalArgumentException("Unable to read uploaded image.");
        }
        if (!hasValidMagicBytes(extension, bytes)) {
            throw new IllegalArgumentException("Uploaded image type does not match the file content.");
        }

        File dir = brandDirectory();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create brand image directory.");
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("Brand image path is not a directory.");
        }

        String filename = UUID.randomUUID() + "." + extension;
        File target = new File(dir, filename);
        try (FileOutputStream outputStream = new FileOutputStream(target)) {
            outputStream.write(bytes);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save brand image.");
        }

        ApplicationBrandAssetResponse response = new ApplicationBrandAssetResponse();
        response.setFilename(filename);
        response.setAssetUrl(ApiConstants.ApplicationSettings.BRAND_ASSET_FULL_PREFIX + filename);
        return response;
    }

    @Override
    public ResponseEntity<Resource> readBrandAsset(String filename) {
        String safeFilename = safeFilename(filename);
        if (safeFilename.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        File file = new File(brandDirectory(), safeFilename);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(file.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        if (!"image".equalsIgnoreCase(mediaType.getType()) || "svg+xml".equalsIgnoreCase(mediaType.getSubtype())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    private String readSetting(String key, String fallback) {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT setting_value FROM pacs_system_settings WHERE setting_key = ?",
                    String.class,
                    key
            );
            String trimmed = trimToNull(value);
            return trimmed == null ? fallback : trimmed;
        } catch (DataAccessException error) {
            return fallback;
        }
    }

    private void upsertSetting(String key, String value, Long modifiedBy) {
        jdbcTemplate.update("""
                INSERT INTO pacs_system_settings (setting_key, setting_value, modified_by, modified_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key) DO UPDATE SET
                    setting_value = EXCLUDED.setting_value,
                    modified_by = EXCLUDED.modified_by,
                    modified_at = CURRENT_TIMESTAMP
                """, key, value, modifiedBy);
    }

    private static String normalizeAppName(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return DEFAULT_APP_NAME;
        }
        if (normalized.length() > MAX_APP_NAME_LENGTH) {
            throw new IllegalArgumentException("Application name is too long.");
        }
        return normalized;
    }

    private static String normalizeBrandUrl(String value, String fallback) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return fallback;
        }
        if (DEFAULT_LOGO_URL.equals(normalized) || DEFAULT_LOGIN_BACKGROUND_URL.equals(normalized)) {
            return normalized;
        }

        String assetPrefix = ApiConstants.ApplicationSettings.BRAND_ASSET_FULL_PREFIX;
        String pacsApiAssetPrefix = "/pacsApi" + assetPrefix;
        if (normalized.startsWith(pacsApiAssetPrefix)) {
            normalized = normalized.substring("/pacsApi".length());
        }
        if (normalized.startsWith(assetPrefix)) {
            String filename = safeFilename(normalized.substring(assetPrefix.length()));
            if (!filename.isEmpty()) {
                return assetPrefix + filename;
            }
        }

        throw new IllegalArgumentException("Upload a brand image before saving.");
    }

    private static String safeFilename(String filename) {
        if (filename == null) {
            return "";
        }
        String normalized = filename.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            return "";
        }
        return normalized.matches(SAFE_FILENAME_REGEX) ? normalized : "";
    }

    private File brandDirectory() {
        return brandAssetDirectory;
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType.trim().toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "image/tiff" -> "tiff";
            default -> "";
        };
    }

    private static boolean hasValidMagicBytes(String extension, byte[] bytes) {
        if (extension == null || bytes == null || bytes.length < 4) {
            return false;
        }
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> bytes.length >= 3
                    && bytes[0] == (byte) 0xFF
                    && bytes[1] == (byte) 0xD8
                    && bytes[2] == (byte) 0xFF;
            case "png" -> bytes.length >= 8
                    && bytes[0] == (byte) 0x89
                    && bytes[1] == (byte) 0x50
                    && bytes[2] == (byte) 0x4E
                    && bytes[3] == (byte) 0x47
                    && bytes[4] == (byte) 0x0D
                    && bytes[5] == (byte) 0x0A
                    && bytes[6] == (byte) 0x1A
                    && bytes[7] == (byte) 0x0A;
            case "gif" -> bytes.length >= 6
                    && bytes[0] == 'G'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == '8'
                    && (bytes[4] == '7' || bytes[4] == '9')
                    && bytes[5] == 'a';
            case "bmp" -> bytes[0] == 'B' && bytes[1] == 'M';
            case "webp" -> bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P';
            case "tiff" -> bytes.length >= 4
                    && ((bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 0x2A && bytes[3] == 0x00)
                    || (bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0x00 && bytes[3] == 0x2A));
            default -> false;
        };
    }
}
