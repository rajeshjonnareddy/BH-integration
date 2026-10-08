package com.example.bullhorn.service;

import com.example.bullhorn.config.BullhornProperties;
import com.example.bullhorn.dto.BullhornDto;
import com.example.bullhorn.repository.RefreshTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for Bullhorn OAuth authentication and session management.
 * Handles refresh token rotation and BhRestToken caching.
 * <p>
 * CRITICAL BEHAVIOR:
 * - Bullhorn invalidates refresh_token on single use and returns a NEW refresh_token
 * - Session tokens (BhRestToken) are cached for ~50-55 minutes
 * - Automatically handles 401 errors by re-authenticating
 */
@Service
public class BullhornAuthService {

    private static final Logger log = LoggerFactory.getLogger(BullhornAuthService.class);

    private final RestClient restClient;
    private final BullhornProperties properties;
    private final RefreshTokenStore tokenStore;
    private final Lock authLock = new ReentrantLock();

    // Session cache
    private volatile String cachedBhRestToken;
    private volatile String cachedRestUrl;
    private volatile Instant sessionExpiresAt;

    public BullhornAuthService(
            RestClient restClient,
            BullhornProperties properties,
            RefreshTokenStore tokenStore) {
        this.restClient = restClient;
        this.properties = properties;
        this.tokenStore = tokenStore;
    }

    /**
     * Get a valid session context (BhRestToken + restUrl).
     * Returns cached session if valid, otherwise re-authenticates.
     *
     * @return session context
     */
    public SessionContext getSession() {
        if (isSessionValid()) {
            log.debug("Using cached session (expires at: {})", sessionExpiresAt);
            return new SessionContext(cachedBhRestToken, cachedRestUrl);
        }

        authLock.lock();
        try {
            // Double-check after acquiring lock
            if (isSessionValid()) {
                return new SessionContext(cachedBhRestToken, cachedRestUrl);
            }

            log.info("Session expired or not initialized. Re-authenticating...");
            return authenticate();
        } finally {
            authLock.unlock();
        }
    }

    /**
     * Clear cached session (e.g., after 401 Unauthorized).
     */
    public void clearSession() {
        authLock.lock();
        try {
            log.info("Clearing cached session");
            cachedBhRestToken = null;
            cachedRestUrl = null;
            sessionExpiresAt = null;
        } finally {
            authLock.unlock();
        }
    }

    /**
     * Perform full OAuth + login flow.
     * Step 1: Exchange refresh_token for access_token (and new refresh_token)
     * Step 2: Use access_token to get BhRestToken and restUrl
     *
     * @return session context
     */
    private SessionContext authenticate() {
        // Step 1: OAuth token exchange
        String accessToken = exchangeRefreshToken();

        // Step 2: Session login
        BullhornDto.LoginResponse loginResponse = sessionLogin(accessToken);

        if (loginResponse.hasError()) {
            log.error("Bullhorn login failed: {} - {}", loginResponse.error(), loginResponse.errorDescription());
            throw new BullhornAuthenticationException(
                    "Login failed: " + loginResponse.error() + " - " + loginResponse.errorDescription());
        }

        // Cache session
        cachedBhRestToken = loginResponse.bhRestToken();
        cachedRestUrl = sanitizeRestUrl(loginResponse.restUrl());
        sessionExpiresAt = Instant.now().plus(properties.session().cacheDuration());

        log.info("Authentication successful. Session expires at: {}", sessionExpiresAt);
        return new SessionContext(cachedBhRestToken, cachedRestUrl);
    }

    /**
     * Exchange refresh_token for access_token.
     * CRITICAL: Captures and persists the NEW refresh_token returned by Bullhorn.
     *
     * @return access_token
     */
    private String exchangeRefreshToken() {
        String currentRefreshToken = tokenStore.get()
                .orElseThrow(() -> new BullhornAuthenticationException(
                        "No refresh token available. Please configure bullhorn.oauth.refresh-token"));

        log.debug("Exchanging refresh token for access token");

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "refresh_token");
        requestBody.add("refresh_token", currentRefreshToken);
        requestBody.add("client_id", properties.oauth().clientId());
        requestBody.add("client_secret", properties.oauth().clientSecret());

        BullhornDto.OAuthResponse oauthResponse = restClient.post()
                .uri(properties.oauth().tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(requestBody)
                .retrieve()
                .body(BullhornDto.OAuthResponse.class);

        if (oauthResponse == null || oauthResponse.accessToken() == null) {
            throw new BullhornAuthenticationException("OAuth token exchange failed: null response");
        }

        // CRITICAL: Persist new refresh token immediately
        if (oauthResponse.refreshToken() != null) {
            log.info("Received new refresh token from Bullhorn (length: {}). Persisting...",
                    oauthResponse.refreshToken().length());
            tokenStore.save(oauthResponse.refreshToken());
        } else {
            log.warn("OAuth response did not include a new refresh_token");
        }

        return oauthResponse.accessToken();
    }

    /**
     * Perform session login to get BhRestToken and restUrl.
     *
     * @param accessToken temporary access token from OAuth
     * @return login response
     */
    private BullhornDto.LoginResponse sessionLogin(String accessToken) {
        log.debug("Performing session login");

        String loginUrl = String.format("%s?version=%s&access_token=%s",
                properties.api().loginUrl(),
                properties.api().version(),
                accessToken);

        BullhornDto.LoginResponse response = restClient.get()
                .uri(loginUrl)
                .retrieve()
                .body(BullhornDto.LoginResponse.class);

        if (response == null) {
            throw new BullhornAuthenticationException("Session login failed: null response");
        }

        return response;
    }

    /**
     * Ensure restUrl ends with slash for proper path concatenation.
     */
    private String sanitizeRestUrl(String restUrl) {
        if (restUrl == null) {
            throw new BullhornAuthenticationException("restUrl is null in login response");
        }
        return restUrl.endsWith("/") ? restUrl : restUrl + "/";
    }

    private boolean isSessionValid() {
        return cachedBhRestToken != null
                && cachedRestUrl != null
                && sessionExpiresAt != null
                && Instant.now().isBefore(sessionExpiresAt);
    }

    /**
     * Session context containing BhRestToken and cluster-specific restUrl.
     */
    public record SessionContext(String bhRestToken, String restUrl) {
    }

    /**
     * Exception thrown when Bullhorn authentication fails.
     */
    public static class BullhornAuthenticationException extends RuntimeException {
        public BullhornAuthenticationException(String message) {
            super(message);
        }

        public BullhornAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
