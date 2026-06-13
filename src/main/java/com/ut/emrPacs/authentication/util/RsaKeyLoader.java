package com.ut.emrPacs.authentication.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;

/**
 * Loads or auto-generates RSA 2048-bit key pair for JWT signing.
 *
 * On first startup (or when PEM files are missing), a new key pair is generated and
 * the keys are persisted to disk so that tokens remain valid across restarts.
 *
 * For production environments, generate keys separately and point the configuration
 * to the key files using absolute paths:
 *   security.jwt.private-key=file:/etc/pacs/keys/private_key.pem
 *   security.jwt.public-key=file:/etc/pacs/keys/public_key.pem
 */
@Component
public class RsaKeyLoader implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RsaKeyLoader.class);

    @Value("${security.jwt.private-key:classpath:key/private_key.pem}")
    private Resource privateKeyResource;

    @Value("${security.jwt.public-key:classpath:key/public_key.pem}")
    private Resource publicKeyResource;

    @Value("${security.jwt.auto-generate-keys:true}")
    private boolean autoGenerateKeys;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @Override
    public void afterPropertiesSet() throws Exception {
        boolean privExists = resourceHasContent(privateKeyResource);
        boolean pubExists  = resourceHasContent(publicKeyResource);

        if (!privExists || !pubExists) {
            if (!autoGenerateKeys) {
                throw new IllegalStateException("RSA key files are missing and security.jwt.auto-generate-keys=false");
            }
            LOGGER.warn("RSA PEM key files not found. Generating new RSA-2048 key pair. " +
                    "IMPORTANT: For production, use persistent key files (see security.jwt.private-key property).");
            KeyPair keyPair = generateKeyPair();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.publicKey  = (RSAPublicKey)  keyPair.getPublic();
            tryPersistToFilesystem(keyPair, !privExists, !pubExists);
        } else {
            this.privateKey = loadPrivateKey(privateKeyResource);
            this.publicKey  = loadPublicKey(publicKeyResource);
            LOGGER.info("RSA key pair loaded from PEM files.");
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    private static boolean resourceHasContent(Resource resource) {
        if (resource == null) return false;
        try {
            return resource.exists() && resource.contentLength() > 10;
        } catch (Exception e) {
            return false;
        }
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        return gen.generateKeyPair();
    }

    private void tryPersistToFilesystem(KeyPair pair, boolean savePrivate, boolean savePublic) {
        try {
            if (savePrivate) {
                File file = tryGetFile(privateKeyResource);
                if (file != null) {
                    writePkcs8Pem(file, pair.getPrivate().getEncoded());
                    LOGGER.info("Private key persisted: {}", file.getAbsolutePath());
                }
            }
            if (savePublic) {
                File file = tryGetFile(publicKeyResource);
                if (file != null) {
                    writeX509Pem(file, pair.getPublic().getEncoded());
                    LOGGER.info("Public key persisted: {}", file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not persist RSA keys to filesystem. Keys are in-memory only (tokens invalidated on restart): {}", e.getMessage());
        }
    }

    private static File tryGetFile(Resource resource) {
        // Standard approach: works when the file already exists or resource is a file: URL
        try {
            return resource.getFile();
        } catch (Exception ignored) {
        }
        // Fallback for ClassPathResource pointing to a not-yet-existing file:
        // resolve the file from the classpath root directory (works in development / exploded JARs)
        if (resource instanceof ClassPathResource cpr) {
            try {
                URL rootUrl = Thread.currentThread().getContextClassLoader().getResource("");
                if (rootUrl != null && "file".equals(rootUrl.getProtocol())) {
                    File rootDir = new File(rootUrl.toURI());
                    return new File(rootDir, cpr.getPath());
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void writePkcs8Pem(File file, byte[] derBytes) throws IOException {
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(derBytes);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(pem.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeX509Pem(File file, byte[] derBytes) throws IOException {
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(derBytes);
        String pem = "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(pem.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static RSAPrivateKey loadPrivateKey(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(is);
        }
    }

    private static RSAPublicKey loadPublicKey(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return RsaKeyConverters.x509().convert(is);
        }
    }
}
