package com.netflix.streaming.platform.watchparty.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned to the host exactly once upon room creation.
 * Contains the raw invitation token to be placed in the client's URL fragment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomResponse {
    private String roomId;
    private Long contentId;
    private String rawInviteToken;
    private String inviteFragment;
    private LiveKitTokenDTO liveKit;
    private RoomSnapshotDTO room;
}

