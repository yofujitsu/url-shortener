package ru.yofujitsu.url_shortener.model.dto;

public record ShortLinkRequestDto(
        String originalUrl,
        int maxClicks
) {
}
