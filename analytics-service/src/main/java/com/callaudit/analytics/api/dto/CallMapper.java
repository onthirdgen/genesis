package com.callaudit.analytics.api.dto;

import com.callaudit.analytics.domain.call.Call;
import com.callaudit.analytics.domain.transcription.Transcription;
import com.callaudit.analytics.domain.transcription.TranscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Mapper for converting between Call entities and DTOs.
 */
@Component
@RequiredArgsConstructor
public class CallMapper {

    private final TranscriptionRepository transcriptionRepository;

    /**
     * Convert Call entity to DTO with transcription status enrichment.
     *
     * @param call the call entity
     * @return the call DTO
     */
    public CallDTO toDTO(Call call) {
        if (call == null) {
            return null;
        }

        CallDTO dto = CallDTO.builder()
                .id(call.getId())
                .callerId(call.getCallerId())
                .agentId(call.getAgentId())
                .channel(call.getChannel() != null ? call.getChannel().name() : null)
                .duration(call.getDuration())
                .status(call.getStatus() != null ? call.getStatus().name() : null)
                .fileFormat(call.getFileFormat())
                .fileSizeBytes(call.getFileSizeBytes())
                .startTime(call.getStartTime())
                .createdAt(call.getCreatedAt())
                .updatedAt(call.getUpdatedAt())
                .build();

        // Enrich with transcription status
        enrichWithTranscriptionStatus(call.getId(), dto);

        return dto;
    }

    /**
     * Convert Page of Call entities to Page of DTOs.
     *
     * @param calls the page of calls
     * @return the page of call DTOs
     */
    public Page<CallDTO> toDTO(Page<Call> calls) {
        return calls.map(this::toDTO);
    }

    /**
     * Enrich DTO with transcription status.
     */
    private void enrichWithTranscriptionStatus(java.util.UUID callId, CallDTO dto) {
        Optional<Transcription> transcription = transcriptionRepository.findByCallId(callId);

        if (transcription.isPresent()) {
            dto.setHasTranscription(true);
            dto.setTranscriptionLanguage(transcription.get().getLanguage());
        } else {
            dto.setHasTranscription(false);
        }
    }
}
