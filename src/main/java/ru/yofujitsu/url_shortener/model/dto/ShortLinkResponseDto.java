package ru.yofujitsu.url_shortener.model.dto;

import java.util.UUID;

public record ShortLinkResponseDto(
        String shortCode,
        String shortUrl,
        UUID userId
) {
}
