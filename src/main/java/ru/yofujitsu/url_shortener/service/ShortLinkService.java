package ru.yofujitsu.url_shortener.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.yofujitsu.url_shortener.model.entity.Link;
import ru.yofujitsu.url_shortener.repository.LinkRepository;
import ru.yofujitsu.url_shortener.utils.ShortCodeGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ShortLinkService {

    private final LinkRepository linkRepository;
    private final NotificationService notificationService;

    @Value("${app.ttl}")
    private int DEFAULT_TTL_HOURS;

    /**
     * Метод создания новой короткой ссылки.
     * Гарантирует уникальность сгенерированного кода для данного пользователя,
     * предпринимая до 6 попыток генерации при коллизии. Срок действия ссылки
     * устанавливается на основе {@code DEFAULT_TTL_HOURS}.
     * * @param userId Идентификатор пользователя, создающего ссылку.
     * @param originalUrl Исходный URL, на который должна указывать короткая ссылка.
     * @param maxClicks Максимальное количество кликов, после которого ссылка будет деактивирована (0 - без лимита).
     * @return Объект Link, представляющий созданную и сохраненную короткую ссылку.
     * @throws IllegalStateException Если не удалось сгенерировать уникальный код после 6 попыток.
     */
    @Transactional
    public Link createLink(UUID userId, String originalUrl, int maxClicks) {

        String code;
        AtomicInteger attempts = new AtomicInteger(0);
        do {
            code = ShortCodeGenerator.generateShortCode(6);
            attempts.getAndIncrement();
            if (attempts.get() > 5) throw new IllegalStateException("Ошибка генерации кода для ссылки");
        } while (linkRepository.existsByCodeAndUserId(code, userId));

        Link link = Link.builder()
                .code(code)
                .originalUrl(originalUrl)
                .userId(userId)
                .maxClicks(maxClicks)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(DEFAULT_TTL_HOURS)))
                .build();
        return linkRepository.save(link);
    }

    /**
     * Метод обработки запроса на редирект по ссылке.
     * Выполняет проверки на активность, срок действия и лимит кликов.
     * При успешном переходе увеличивает счетчик кликов.
     * Деактивирует ссылку, если она истекла или исчерпала лимит, и отправляет уведомление пользователю.
     *
     * @param code Короткий код ссылки.
     * @return Optional, содержащий исходный URL, если ссылка активна и доступна для перехода,
     * или Optional.empty(), если ссылка не найдена, неактивна, истекла или исчерпала лимит.
     */
    @Transactional
    public Optional<String> handleRedirect(String code) {
        Optional<Link> opt = linkRepository.findByCode(code);
        if (opt.isEmpty()) return Optional.empty();

        Link link = opt.get();
        if (!link.isActive()) {
            notificationService.sendNotification(link.getUserId(), "Попытка перехода по неактивной ссылке: " + code);
            return Optional.empty();
        }

        if (link.getExpiresAt().isBefore(Instant.now())) {
            link.setActive(false);
            linkRepository.save(link);
            notificationService.sendNotification(link.getUserId(), "Ссылка истекла: " + code);
            return Optional.empty();
        }

        if (link.getMaxClicks() > 0 && link.getClicks().get() >= link.getMaxClicks()) {
            link.setActive(false);
            linkRepository.save(link);
            notificationService.sendNotification(link.getUserId(), "Лимит переходов исчерпан: " + code);
            return Optional.empty();
        }

        AtomicInteger clicks = link.getClicks();
        clicks.incrementAndGet();
        link.setClicks(clicks);
        if (link.getMaxClicks() > 0 && link.getClicks().get() >= link.getMaxClicks()) {
            link.setActive(false);
            notificationService.sendNotification(link.getUserId(), "Лимит переходов исчерпан: " + code);
        }
        linkRepository.save(link);

        return Optional.of(link.getOriginalUrl());
    }
}
