package com.callaudit.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallAuditedEvent {

    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private String timestamp;
    private Integer version;
    private String causationId;
    private String correlationId;
    private Map<String, Object> metadata;
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private String callId;
        private String auditStatus;
        private Double qualityScore;
        private ComplianceCheck complianceCheck;
        private List<Violation> violations;
        private List<ComplianceItem> complianceItems;
        private String auditedAt;
        private String auditorId;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComplianceCheck {
            private Boolean passed;
            private Double score;
            private List<String> failedChecks;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Violation {
            private String type;
            private String description;
            private String severity;
            private String timestamp;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComplianceItem {
            private String checkName;
            private Boolean passed;
            private String details;
        }
    }
}
