package com.example.bullhorn.controller;

import com.example.bullhorn.dto.TimesheetResponseDto;
import com.example.bullhorn.service.BullhornTimesheetService;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for querying Bullhorn timesheets.
 * Provides endpoints for employers to retrieve timesheet data by candidate IDs.
 */
@RestController
@RequestMapping("/api/v1/timesheets")
@Validated
public class TimesheetController {

    private static final Logger log = LoggerFactory.getLogger(TimesheetController.class);

    private final BullhornTimesheetService timesheetService;

    public TimesheetController(BullhornTimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    /**
     * Query timesheets by candidate IDs.
     * <p>
     * Example request:
     * POST /api/v1/timesheets/query
     * {
     * "candidateIds": [101, 102, 103]
     * }
     *
     * @param request request containing list of candidate IDs
     * @return timesheet response
     */
    @PostMapping("/query")
    public ResponseEntity<TimesheetResponseDto.TimesheetsResponse> queryTimesheets(
            @RequestBody @Validated TimesheetQueryRequest request) {

        log.info("Received timesheet query request for {} candidate IDs", request.candidateIds().size());

        TimesheetResponseDto.TimesheetsResponse response =
                timesheetService.getTimesheetsByCandidateIds(request.candidateIds());

        log.info("Returning {} timesheets for {} candidates",
                response.timesheets().size(), request.candidateIds().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Query timesheets by candidate IDs (GET variant with query params).
     * <p>
     * Example: GET /api/v1/timesheets?candidateIds=101,102,103
     *
     * @param candidateIds comma-separated list of candidate IDs
     * @return timesheet response
     */
    @GetMapping
    public ResponseEntity<TimesheetResponseDto.TimesheetsResponse> getTimesheets(
            @RequestParam @NotEmpty List<Integer> candidateIds) {

        log.info("Received timesheet GET request for {} candidate IDs", candidateIds.size());

        TimesheetResponseDto.TimesheetsResponse response =
                timesheetService.getTimesheetsByCandidateIds(candidateIds);

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "Bullhorn Timesheet Service"));
    }

    /**
     * Request DTO for timesheet queries.
     */
    public record TimesheetQueryRequest(
            @NotEmpty(message = "candidateIds must not be empty")
            List<Integer> candidateIds
    ) {
    }

    /**
     * Health check response.
     */
    public record HealthResponse(String status, String service) {
    }
}
