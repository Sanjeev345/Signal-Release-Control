package com.hackathon.podcast.excerpt.web.dto;

import jakarta.validation.constraints.Size;

public record RejectExcerptRequest(@Size(max = 2000) String reason) {
}
