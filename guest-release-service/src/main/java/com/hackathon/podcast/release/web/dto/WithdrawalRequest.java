package com.hackathon.podcast.release.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawalRequest(
        @NotBlank(message = "A withdrawal reason is required")
        @Size(max = 2000)
        String reason
) {
}
