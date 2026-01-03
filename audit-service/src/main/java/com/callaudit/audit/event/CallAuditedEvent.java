package com.callaudit.audit.event;

import com.callaudit.audit.model.ComplianceStatus;
import com.callaudit.audit.model.ViolationSeverity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class CallAuditedEvent extends BaseEvent {

    @JsonProperty("payload")
    private AuditPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditPayload {
        @JsonProperty("callId")
        private UUID callId;

        @JsonProperty("overallScore")
        private Integer overallScore;

        @JsonProperty("complianceStatus")
        private ComplianceStatus complianceStatus;

        @JsonProperty("violations")
        private List<ViolationInfo> violations;

        @JsonProperty("qualityMetrics")
        private QualityMetrics qualityMetrics;

        @JsonProperty("flagsForReview")
        private Boolean flagsForReview;

        @JsonProperty("reviewReason")
        private String reviewReason;

        @JsonProperty("processingTimeMs")
        private Integer processingTimeMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViolationInfo {
        @JsonProperty("ruleId")
        private String ruleId;

        @JsonProperty("ruleName")
        private String ruleName;

        @JsonProperty("severity")
        private ViolationSeverity severity;

        @JsonProperty("description")
        private String description;

        @JsonProperty("timestampInCall")
        private BigDecimal timestampInCall;

        @JsonProperty("evidence")
        private String evidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityMetrics {
        @JsonProperty("scriptAdherence")
        private Integer scriptAdherence;

        @JsonProperty("customerService")
        private Integer customerService;

        @JsonProperty("resolutionEffectiveness")
        private Integer resolutionEffectiveness;
    }
}
