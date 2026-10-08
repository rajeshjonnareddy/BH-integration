package com.example.bullhorn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bullhorn API response DTOs.
 * These records map directly to Bullhorn's JSON response structure.
 */
public class BullhornDto {

    /**
     * OAuth token response from https://auth.bullhornstaffing.com/oauth/token
     */
    public record OAuthResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Integer expiresIn
    ) {
    }

    /**
     * Session login response from /rest-services/login
     */
    public record LoginResponse(
            @JsonProperty("BhRestToken") String bhRestToken,
            @JsonProperty("restUrl") String restUrl,
            @JsonProperty("error") String error,
            @JsonProperty("error_description") String errorDescription
    ) {
        public boolean hasError() {
            return error != null && !error.isBlank();
        }
    }

    /**
     * Timesheet query response from /query/Timesheet
     */
    public record TimesheetQueryResponse(
            List<TimesheetData> data,
            Integer count,
            Integer start,
            String error,
            @JsonProperty("error_description") String errorDescription
    ) {
        public boolean hasError() {
            return error != null && !error.isBlank();
        }
    }

    /**
     * Individual timesheet data structure
     */
    public record TimesheetData(
            Integer id,
            String status,
            Long dateAdded,
            String comments,
            BigDecimal hours,
            PlacementInfo placement
    ) {
    }

    /**
     * Nested placement information
     */
    public record PlacementInfo(
            Integer id,
            CandidateInfo candidate,
            JobOrderInfo jobOrder
    ) {
    }

    /**
     * Nested candidate information
     */
    public record CandidateInfo(
            Integer id,
            String firstName,
            String lastName
    ) {
    }

    /**
     * Nested job order information
     */
    public record JobOrderInfo(
            Integer id,
            String title
    ) {
    }
}
