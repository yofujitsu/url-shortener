package ru.yofujitsu.url_shortener.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yofujitsu.url_shortener.model.dto.ShortLinkRequestDto;
import ru.yofujitsu.url_shortener.model.dto.ShortLinkResponseDto;
import ru.yofujitsu.url_shortener.model.entity.Link;
import ru.yofujitsu.url_shortener.service.ShortLinkService;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LinkController {
    private final ShortLinkService shortLinkService;
    @Value("${app.base-url}")
    private String baseUrl;

    @PostMapping("/shorten")
    public ResponseEntity<?> createShort(@RequestHeader(value = "X-User-ID", required = false) UUID userId,
                                         @RequestBody ShortLinkRequestDto shortLinkRequestDto,
                                         HttpServletResponse response) {
        if (userId == null) {
            userId = UUID.randomUUID();
            Cookie cookie = new Cookie("SHORTLINK_USER", userId.toString());
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24 * 365);
            response.addCookie(cookie);
        }
        Link link = shortLinkService.createLink(userId, shortLinkRequestDto.originalUrl(), shortLinkRequestDto.maxClicks());
        return ResponseEntity.ok(new ShortLinkResponseDto(link.getCode(), baseUrl + link.getCode(), userId));
    }

    @GetMapping("/{code}")
    public void redirect(@PathVariable String code, HttpServletResponse response) throws IOException {
        Optional<String> target = shortLinkService.handleRedirect(code);
        if (target.isPresent()) {
            response.sendRedirect(target.get());
        } else {
            response.sendError(HttpStatus.NOT_FOUND.value(), "Ссылка не доступна");
        }
    }
}
