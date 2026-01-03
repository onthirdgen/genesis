package com.callaudit.analytics.api;

import com.callaudit.analytics.api.dto.CallDTO;
import com.callaudit.analytics.api.dto.CallMapper;
import com.callaudit.analytics.domain.call.Call;
import com.callaudit.analytics.domain.call.CallQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST controller for call queries.
 * Part of the call bounded context within analytics-service.
 *
 * This controller serves call data from the read model,
 * which is populated by consuming CallReceived events from Kafka.
 */
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Calls", description = "Call query endpoints")
public class CallsController {

    private final CallQueryService queryService;
    private final CallMapper mapper;

    /**
     * List calls with pagination and optional filters.
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @param status filter by status
     * @param agentId filter by agent ID
     * @param callerId filter by caller ID
     * @param startDate filter by start date (from)
     * @param endDate filter by end date (to)
     * @return page of calls
     */
    @GetMapping
    @Operation(
            summary = "List calls with pagination",
            description = "Retrieves a paginated list of calls with optional filters for status, agent, caller, and date range"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Calls retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Page.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid parameters",
                    content = @Content
            )
    })
    public ResponseEntity<Page<CallDTO>> listCalls(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by agent ID") @RequestParam(required = false) String agentId,
            @Parameter(description = "Filter by caller ID") @RequestParam(required = false) String callerId,
            @Parameter(description = "Filter by start date (from)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Filter by end date (to)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Received request to list calls - page: {}, size: {}, status: {}, agentId: {}, callerId: {}, startDate: {}, endDate: {}",
                page, size, status, agentId, callerId, startDate, endDate);

        Pageable pageable = PageRequest.of(page, size);
        Page<Call> calls;

        // Apply filters
        if (startDate != null && endDate != null) {
            calls = queryService.findByDateRange(startDate, endDate, pageable);
        } else if (status != null) {
            try {
                Call.Status callStatus = Call.Status.valueOf(status.toUpperCase());
                calls = queryService.findByStatus(callStatus, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", status);
                return ResponseEntity.badRequest().build();
            }
        } else if (agentId != null) {
            calls = queryService.findByAgentId(agentId, pageable);
        } else if (callerId != null) {
            calls = queryService.findByCallerId(callerId, pageable);
        } else {
            calls = queryService.findAll(pageable);
        }

        Page<CallDTO> dtos = mapper.toDTO(calls);

        log.info("Successfully retrieved {} calls (page {} of {})",
                dtos.getNumberOfElements(), dtos.getNumber(), dtos.getTotalPages());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get call by ID.
     *
     * @param id the call ID
     * @return the call
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get call by ID",
            description = "Retrieves a single call by its ID"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Call found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CallDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Call not found",
                    content = @Content
            )
    })
    public ResponseEntity<CallDTO> getCall(
            @Parameter(description = "Call ID (UUID format)", required = true)
            @PathVariable UUID id) {

        log.info("Received request for call with ID: {}", id);

        try {
            Call call = queryService.getById(id);
            CallDTO dto = mapper.toDTO(call);

            log.info("Successfully retrieved call with ID: {}", id);
            return ResponseEntity.ok(dto);

        } catch (CallQueryService.CallNotFoundException e) {
            log.warn("Call not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
