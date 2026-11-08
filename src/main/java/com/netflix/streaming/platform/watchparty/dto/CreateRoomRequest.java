package com.netflix.streaming.platform.watchparty.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotNull(message = "contentId is required")
    private Long contentId;

    @NotNull(message = "profileId is required")
    private Long profileId;
}
