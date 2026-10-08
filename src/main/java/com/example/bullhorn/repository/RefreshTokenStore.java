package com.example.bullhorn.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstraction for persisting Bullhorn refresh tokens.
 * CRITICAL: Bullhorn invalidates refresh tokens on single use and returns a new one.
 * The implementation must persist the new token immediately upon rotation.
 */
public interface RefreshTokenStore {

    /**
     * Persist the new refresh token.
     * Called immediately after OAuth token exchange.
     *
     * @param refreshToken the new refresh token from Bullhorn
     */
    void save(String refreshToken);

    /**
     * Retrieve the current refresh token.
     *
     * @return the current refresh token, or empty if not available
     */
    Optional<String> get();

    /**
     * In-memory implementation with thread-safe AtomicReference.
     * Suitable for development and testing.
     * WARNING: Tokens are lost on application restart.
     */
    @Component
    @Profile("dev")
    class InMemoryRefreshTokenStore implements RefreshTokenStore {
        private static final Logger log = LoggerFactory.getLogger(InMemoryRefreshTokenStore.class);
        private final AtomicReference<String> tokenRef = new AtomicReference<>();

        public InMemoryRefreshTokenStore(@Value("${bullhorn.oauth.refresh-token}") String initialToken) {
            if (initialToken != null && !initialToken.isBlank() && !initialToken.equals("PLACEHOLDER")) {
                tokenRef.set(initialToken);
                log.info("Initialized in-memory refresh token store with token from configuration");
            } else {
                log.warn("No valid initial refresh token provided. Waiting for runtime token.");
            }
        }

        @Override
        public void save(String refreshToken) {
            tokenRef.set(refreshToken);
            log.info("Refresh token updated in memory (length: {})", refreshToken != null ? refreshToken.length() : 0);
        }

        @Override
        public Optional<String> get() {
            return Optional.ofNullable(tokenRef.get());
        }
    }

    /**
     * File-based implementation for persistence across restarts.
     * Writes tokens to a secure file location.
     * Suitable for environments without a database or vault.
     */
    @Component
    @Profile("file-storage")
    class FileRefreshTokenStore implements RefreshTokenStore {
        private static final Logger log = LoggerFactory.getLogger(FileRefreshTokenStore.class);
        private final Path tokenFilePath;
        private final AtomicReference<String> cachedToken = new AtomicReference<>();

        public FileRefreshTokenStore(
                @Value("${bullhorn.oauth.refresh-token}") String initialToken,
                @Value("${bullhorn.token-storage.file-path:./bullhorn-refresh-token.txt}") String filePath) {

            this.tokenFilePath = Paths.get(filePath);

            // Try to load from file first, then fall back to config
            Optional<String> fileToken = loadFromFile();
            if (fileToken.isPresent()) {
                cachedToken.set(fileToken.get());
                log.info("Loaded refresh token from file: {}", tokenFilePath);
            } else if (initialToken != null && !initialToken.isBlank() && !initialToken.equals("PLACEHOLDER")) {
                cachedToken.set(initialToken);
                save(initialToken); // Persist to file
                log.info("Initialized file-based refresh token store from configuration");
            } else {
                log.warn("No valid refresh token in file or configuration");
            }
        }

        @Override
        public void save(String refreshToken) {
            cachedToken.set(refreshToken);
            try {
                Files.writeString(tokenFilePath, refreshToken,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                log.info("Refresh token persisted to file: {}", tokenFilePath);
            } catch (IOException e) {
                log.error("Failed to persist refresh token to file: {}", tokenFilePath, e);
                throw new RuntimeException("Failed to persist refresh token", e);
            }
        }

        @Override
        public Optional<String> get() {
            return Optional.ofNullable(cachedToken.get());
        }

        private Optional<String> loadFromFile() {
            try {
                if (Files.exists(tokenFilePath)) {
                    String token = Files.readString(tokenFilePath).trim();
                    return token.isBlank() ? Optional.empty() : Optional.of(token);
                }
            } catch (IOException e) {
                log.warn("Could not read refresh token from file: {}", tokenFilePath, e);
            }
            return Optional.empty();
        }
    }

    /**
     * Placeholder implementation for production environments.
     * TODO: Replace with actual database/vault implementation (e.g., Spring Data JPA, HashiCorp Vault).
     */
    @Component
    @Profile("prod")
    class ProductionRefreshTokenStore implements RefreshTokenStore {
        private static final Logger log = LoggerFactory.getLogger(ProductionRefreshTokenStore.class);
        private final AtomicReference<String> tokenRef = new AtomicReference<>();

        public ProductionRefreshTokenStore(@Value("${bullhorn.oauth.refresh-token}") String initialToken) {
            if (initialToken != null && !initialToken.isBlank() && !initialToken.equals("PLACEHOLDER")) {
                tokenRef.set(initialToken);
                log.warn("Production token store using in-memory fallback. Implement persistent storage!");
            }
        }

        @Override
        public void save(String refreshToken) {
            tokenRef.set(refreshToken);
            log.warn("TODO: Implement production-grade refresh token persistence (DB/Vault). Token length: {}",
                    refreshToken != null ? refreshToken.length() : 0);
            // TODO: Save to database or vault
            // Example: vaultTemplate.write("secret/bullhorn/refresh-token", Map.of("token", refreshToken));
        }

        @Override
        public Optional<String> get() {
            // TODO: Retrieve from database or vault
            return Optional.ofNullable(tokenRef.get());
        }
    }
}
