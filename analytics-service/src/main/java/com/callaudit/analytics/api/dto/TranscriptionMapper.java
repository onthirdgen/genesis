package com.callaudit.analytics.api.dto;

import com.callaudit.analytics.domain.transcription.Transcription;
import com.callaudit.analytics.domain.transcription.TranscriptionSegment;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Mapper for converting between Transcription entities and DTOs.
 */
@Component
public class TranscriptionMapper {

    /**
     * Convert Transcription entity to DTO.
     *
     * @param transcription the transcription entity
     * @return the transcription DTO
     */
    public TranscriptionDTO toDTO(Transcription transcription) {
        if (transcription == null) {
            return null;
        }

        return TranscriptionDTO.builder()
                .id(transcription.getId())
                .callId(transcription.getCallId())
                .fullText(transcription.getFullText())
                .language(transcription.getLanguage())
                .confidence(transcription.getConfidence())
                .wordCount(transcription.getWordCount())
                .processingTimeMs(transcription.getProcessingTimeMs())
                .modelVersion(transcription.getModelVersion())
                .createdAt(transcription.getCreatedAt())
                .segments(transcription.getSegments().stream()
                        .map(this::toSegmentDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Convert TranscriptionSegment entity to DTO.
     *
     * @param segment the segment entity
     * @return the segment DTO
     */
    private TranscriptionDTO.SegmentDTO toSegmentDTO(TranscriptionSegment segment) {
        if (segment == null) {
            return null;
        }

        return TranscriptionDTO.SegmentDTO.builder()
                .id(segment.getId())
                .speaker(segment.getSpeaker() != null ? segment.getSpeaker().name() : null)
                .startTime(segment.getStartTime())
                .endTime(segment.getEndTime())
                .text(segment.getText())
                .confidence(segment.getConfidence())
                .wordCount(segment.getWordCount())
                .build();
    }
}
