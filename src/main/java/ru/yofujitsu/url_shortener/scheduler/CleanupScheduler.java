package ru.yofujitsu.url_shortener.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yofujitsu.url_shortener.model.entity.Link;
import ru.yofujitsu.url_shortener.repository.LinkRepository;
import ru.yofujitsu.url_shortener.service.NotificationService;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final LinkRepository linkRepository;
    private final NotificationService notificationService;

    /**
     * Планировщик периодически проверяет и деактивирует просроченные короткие ссылки.
     * Находит все ссылки, у которых {@code expiresAt} меньше текущего времени.
     * Для активных просроченных ссылок устанавливает {@code isActive = false} и отправляет уведомление пользователю.
     * Удаляет все найденные просроченные записи из репозитория.
     */
    @Scheduled(fixedDelayString = "${app.cleanup-interval-ms}")
    @Transactional
    public void cleanupExpired() {
        Instant now = Instant.now();
        List<Link> expired = linkRepository.findByExpiresAtBefore(now);
        for (Link link : expired) {
            if (link.isActive()) {
                link.setActive(false);
                linkRepository.save(link);
                notificationService.sendNotification(link.getUserId(), "Ссылка истекла и была деактивирована: " + link.getCode());
            }
            linkRepository.deleteAll(expired);
        }
    }
}
