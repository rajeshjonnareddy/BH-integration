package com.example.bullhorn.service;

import com.example.bullhorn.dto.BullhornDto;
import com.example.bullhorn.dto.TimesheetResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for querying Bullhorn timesheets.
 * Handles automatic re-authentication on 401 errors.
 */
@Service
public class BullhornTimesheetService {

    private static final Logger log = LoggerFactory.getLogger(BullhornTimesheetService.class);

    private static final String TIMESHEET_FIELDS =
            "id,status,dateAdded,comments,hours," +
                    "placement(id,candidate(id,firstName,lastName),jobOrder(id,title))";

    private final RestClient restClient;
    private final BullhornAuthService authService;

    public BullhornTimesheetService(RestClient restClient, BullhornAuthService authService) {
        this.restClient = restClient;
        this.authService = authService;
    }

    /**
     * Query timesheets for the specified candidate IDs.
     *
     * @param candidateIds list of candidate IDs to filter by
     * @return timesheet response
     */
    public TimesheetResponseDto.TimesheetsResponse getTimesheetsByCandidateIds(List<Integer> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            log.warn("No candidate IDs provided for timesheet query");
            return new TimesheetResponseDto.TimesheetsResponse(List.of(), 0, List.of());
        }

        log.info("Querying timesheets for {} candidate IDs: {}", candidateIds.size(), candidateIds);

        String whereClause = buildWhereClause(candidateIds);
        BullhornDto.TimesheetQueryResponse response = queryTimesheets(whereClause, false);

        return mapToResponseDto(response, candidateIds);
    }

    /**
     * Execute the Bullhorn timesheet query.
     * Handles 401 by clearing session and retrying once.
     *
     * @param whereClause JPQL where clause
     * @param isRetry     whether this is a retry after 401
     * @return query response
     */
    private BullhornDto.TimesheetQueryResponse queryTimesheets(String whereClause, boolean isRetry) {
        BullhornAuthService.SessionContext session = authService.getSession();

        String queryUrl = session.restUrl() + "query/Timesheet";

        try {
            BullhornDto.TimesheetQueryResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("")
                            .queryParam("where", whereClause)
                            .queryParam("fields", TIMESHEET_FIELDS)
                            .queryParam("count", 500)
                            .build(queryUrl))
                    .header("BhRestToken", session.bhRestToken())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, httpResponse) -> {
                        if (httpResponse.getStatusCode().value() == 401 && !isRetry) {
                            log.warn("Received 401 Unauthorized. Clearing session and retrying...");
                            authService.clearSession();
                        }
                    })
                    .body(BullhornDto.TimesheetQueryResponse.class);

            if (response != null && response.hasError()) {
                log.error("Bullhorn query error: {} - {}", response.error(), response.errorDescription());
                throw new BullhornQueryException(
                        "Query failed: " + response.error() + " - " + response.errorDescription());
            }

            return response;

        } catch (Exception e) {
            if (!isRetry && e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("Retrying query after authentication refresh");
                return queryTimesheets(whereClause, true);
            }
            throw new BullhornQueryException("Failed to query timesheets", e);
        }
    }

    /**
     * Build JPQL WHERE clause for candidate ID filtering.
     */
    private String buildWhereClause(List<Integer> candidateIds) {
        String idsClause = candidateIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return String.format("placement.candidate.id IN (%s) AND isDeleted=false", idsClause);
    }

    /**
     * Map Bullhorn query response to clean domain DTOs.
     */
    private TimesheetResponseDto.TimesheetsResponse mapToResponseDto(
            BullhornDto.TimesheetQueryResponse response, List<Integer> requestedIds) {

        if (response == null || response.data() == null) {
            return new TimesheetResponseDto.TimesheetsResponse(List.of(), 0, requestedIds);
        }

        List<TimesheetResponseDto.Timesheet> timesheets = response.data().stream()
                .map(this::mapTimesheet)
                .toList();

        return new TimesheetResponseDto.TimesheetsResponse(
                timesheets,
                response.count() != null ? response.count() : timesheets.size(),
                requestedIds
        );
    }

    /**
     * Map individual timesheet data to domain DTO.
     */
    private TimesheetResponseDto.Timesheet mapTimesheet(BullhornDto.TimesheetData data) {
        TimesheetResponseDto.PlacementDetails placement = null;

        if (data.placement() != null) {
            TimesheetResponseDto.Candidate candidate = null;
            if (data.placement().candidate() != null) {
                BullhornDto.CandidateInfo c = data.placement().candidate();
                candidate = new TimesheetResponseDto.Candidate(
                        c.id(), c.firstName(), c.lastName());
            }

            TimesheetResponseDto.JobOrder jobOrder = null;
            if (data.placement().jobOrder() != null) {
                BullhornDto.JobOrderInfo j = data.placement().jobOrder();
                jobOrder = new TimesheetResponseDto.JobOrder(j.id(), j.title());
            }

            placement = new TimesheetResponseDto.PlacementDetails(
                    data.placement().id(), candidate, jobOrder);
        }

        Instant dateAdded = data.dateAdded() != null
                ? Instant.ofEpochMilli(data.dateAdded())
                : null;

        return new TimesheetResponseDto.Timesheet(
                data.id(),
                data.status(),
                dateAdded,
                data.comments(),
                data.hours(),
                placement
        );
    }

    /**
     * Exception thrown when Bullhorn query fails.
     */
    public static class BullhornQueryException extends RuntimeException {
        public BullhornQueryException(String message) {
            super(message);
        }

        public BullhornQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
