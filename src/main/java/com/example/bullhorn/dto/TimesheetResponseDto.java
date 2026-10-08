package com.example.bullhorn.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Clean domain DTOs for external API clients.
 * These provide a simplified, domain-focused view of timesheet data.
 */
public class TimesheetResponseDto {

    /**
     * Response wrapper for timesheet queries
     */
    public record TimesheetsResponse(
            List<Timesheet> timesheets,
            int totalCount,
            List<Integer> requestedCandidateIds
    ) {
    }

    /**
     * Clean timesheet record for API consumers
     */
    public record Timesheet(
            int timesheetId,
            String status,
            Instant dateAdded,
            String comments,
            BigDecimal hours,
            PlacementDetails placement
    ) {
    }

    /**
     * Placement details with candidate and job information
     */
    public record PlacementDetails(
            int placementId,
            Candidate candidate,
            JobOrder jobOrder
    ) {
    }

    /**
     * Candidate details
     */
    public record Candidate(
            int candidateId,
            String firstName,
            String lastName,
            String fullName
    ) {
        public Candidate(int candidateId, String firstName, String lastName) {
            this(candidateId, firstName, lastName,
                    String.format("%s %s", firstName != null ? firstName : "",
                            lastName != null ? lastName : "").trim());
        }
    }

    /**
     * Job order details
     */
    public record JobOrder(
            int jobOrderId,
            String title
    ) {
    }
}
