package ru.yofujitsu.url_shortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yofujitsu.url_shortener.model.entity.Notification;
import ru.yofujitsu.url_shortener.repository.NotificationRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Метод создания нового уведомления для пользователя.
     * * @param userId Идентификатор пользователя, которому отправляется уведомление.
     * @param message Текст уведомления.
     */
    public void sendNotification(UUID userId, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .message(message)
                .build();
        notificationRepository.save(notification);
        log.info("Отправлено уведомление пользователю {}: {}", userId, message);
    }
}