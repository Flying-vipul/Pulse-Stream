package com.netflix.streaming.platform.watchparty.dto;

import com.netflix.streaming.platform.watchparty.domain.Participant;
import com.netflix.streaming.platform.watchparty.domain.ParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDTO {
    private Long userId;
    private Long profileId;
    private String displayName;
    private String avatarUrl;
    private ParticipantStatus status;
    private boolean isHost;
    private long joinedAtMs;

    public static ParticipantDTO from(Participant participant) {
        if (participant == null) {
            return null;
        }
        return ParticipantDTO.builder()
                .userId(participant.getUserId())
                .profileId(participant.getProfileId())
                .displayName(participant.getDisplayName())
                .avatarUrl(participant.getAvatarUrl())
                .status(participant.getStatus())
                .isHost(participant.isHost())
                .joinedAtMs(participant.getJoinedAtMs())
                .build();
    }
}
