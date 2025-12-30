package com.callauditing.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaMessage {
    private long offset;
    private int partition;
    private String timestamp;
    private String key;
    private String value;
    private String topic;
}
