package ru.yofujitsu.url_shortener.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.yofujitsu.url_shortener.model.entity.Link;
import ru.yofujitsu.url_shortener.repository.LinkRepository;
import ru.yofujitsu.url_shortener.utils.ShortCodeGenerator;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTests {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ShortLinkService shortLinkService;

    private final UUID TEST_USER_ID = UUID.randomUUID();
    private final String TEST_URL = "https://test.com";
    private final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final int MOCK_TTL_HOURS = 24;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shortLinkService, "DEFAULT_TTL_HOURS", MOCK_TTL_HOURS * 60);
    }

    /**
     * Проверяет успешное создание новой короткой ссылки.
     * Моделируется ситуация, когда сгенерированный код уникален с первой попытки.
     * Ожидается, что ссылка успешно сохраняется в репозиторий и содержит корректные поля.
     */
    @Test
    void createLink_Success_NewLinkSaved() {
        try (MockedStatic<ShortCodeGenerator> mockedGenerator = mockStatic(ShortCodeGenerator.class)) {
            mockedGenerator.when(() -> ShortCodeGenerator.generateShortCode(6)).thenReturn("UNIQUE6");
            when(linkRepository.existsByCodeAndUserId("UNIQUE6", TEST_USER_ID)).thenReturn(false);
            when(linkRepository.save(any(Link.class))).thenAnswer(i -> i.getArguments()[0]);

            Link createdLink = shortLinkService.createLink(TEST_USER_ID, TEST_URL, 10);

            assertNotNull(createdLink);
            assertEquals("UNIQUE6", createdLink.getCode());
            assertEquals(TEST_URL, createdLink.getOriginalUrl());
            assertEquals(10, createdLink.getMaxClicks());

            assertTrue(createdLink.getExpiresAt().isAfter(Instant.now().plus(Duration.ofHours(23)).minus(Duration.ofMinutes(1))));

            verify(linkRepository, times(1)).save(any(Link.class));
        }
    }

    /**
     * Проверяет успешное создание короткой ссылки после одной коллизии кода.
     * Первая генерация возвращает уже существующий код, вторая — уникальный.
     * Проверяется корректность повторных попыток и итоговое сохранение ссылки.
     */
    @Test
    void createLink_Success_AfterCollisionAttempt() {
        try (MockedStatic<ShortCodeGenerator> mockedGenerator = mockStatic(ShortCodeGenerator.class)) {
            mockedGenerator.when(() -> ShortCodeGenerator.generateShortCode(6)).thenReturn("CODE1", "CODE2");

            when(linkRepository.existsByCodeAndUserId("CODE1", TEST_USER_ID)).thenReturn(true);
            when(linkRepository.existsByCodeAndUserId("CODE2", TEST_USER_ID)).thenReturn(false);
            when(linkRepository.save(any(Link.class))).thenAnswer(i -> i.getArguments()[0]);

            Link createdLink = shortLinkService.createLink(TEST_USER_ID, TEST_URL, 10);

            assertEquals("CODE2", createdLink.getCode());
            verify(linkRepository, times(1)).save(any(Link.class));
            verify(linkRepository, times(2)).existsByCodeAndUserId(anyString(), eq(TEST_USER_ID));
        }
    }

    /**
     * Проверяет ситуацию, когда превышено число попыток генерации уникального кода.
     * Код постоянно коллидирует, и после 5 неудачных попыток выбрасывается IllegalStateException.
     * Репозиторий не должен сохранить ни одной записи.
     */
    @Test
    void createLink_Failure_CodeGenerationAttemptsExceeded() {
        try (MockedStatic<ShortCodeGenerator> mockedGenerator = mockStatic(ShortCodeGenerator.class)) {
            mockedGenerator.when(() -> ShortCodeGenerator.generateShortCode(6)).thenReturn("CODE1");
            when(linkRepository.existsByCodeAndUserId(eq("CODE1"), any())).thenReturn(true);

            assertThrows(IllegalStateException.class, () -> shortLinkService.createLink(TEST_USER_ID, TEST_URL, 10));

            verify(linkRepository, times(5)).existsByCodeAndUserId(anyString(), any());
            verify(linkRepository, never()).save(any(Link.class));
        }
    }

    /**
     * Проверяет успешный редирект по активной ссылке.
     * Ссылка найдена, не истекла и не исчерпала лимит переходов.
     * Ожидается увеличение счётчика кликов и возврат originalUrl без уведомлений.
     */
    @Test
    void handleRedirect_Success_LinkFoundAndIncremented() {
        Link activeLink = Link.builder().code("TEST").originalUrl(TEST_URL).maxClicks(10).expiresAt(NOW.plus(Duration.ofHours(1))).build();
        when(linkRepository.findByCode("TEST")).thenReturn(Optional.of(activeLink));

        Optional<String> result = shortLinkService.handleRedirect("TEST");

        assertTrue(result.isPresent());
        assertEquals(TEST_URL, result.get());
        assertEquals(1, activeLink.getClicks().get());
        verify(linkRepository, times(1)).save(activeLink);
        verify(notificationService, never()).sendNotification(any(), any());
    }

    /**
     * Проверяет поведение при попытке перехода по истекшей ссылке.
     * Ссылка становится неактивной, пользователь получает уведомление,
     * переход не выполняется.
     */
    @Test
    void handleRedirect_Failure_LinkExpired() {

        Link expiredLink = Link.builder().code("EXPIRED").originalUrl(TEST_URL).userId(TEST_USER_ID)
                .expiresAt(NOW.minus(Duration.ofMinutes(1))).build();

        when(linkRepository.findByCode("EXPIRED")).thenReturn(Optional.of(expiredLink));

        Optional<String> result = shortLinkService.handleRedirect("EXPIRED");

        assertTrue(result.isEmpty());
        assertFalse(expiredLink.isActive());
        verify(notificationService, times(1)).sendNotification(eq(TEST_USER_ID), contains("Ссылка истекла"));
        verify(linkRepository, times(1)).save(expiredLink);
    }

    /**
     * Проверяет отказ в редиректе при уже исчерпанном лимите переходов.
     * При обращении к такой ссылке выполняется деактивация и отправка уведомления пользователю.
     */
    @Test
    void handleRedirect_Failure_MaxClicksReached_InitialCheck() {

        Link maxedLink = Link.builder().code("MAXED").originalUrl(TEST_URL).userId(TEST_USER_ID).maxClicks(5).expiresAt(NOW.plus(Duration.ofHours(1))).build();
        maxedLink.getClicks().set(5);
        when(linkRepository.findByCode("MAXED")).thenReturn(Optional.of(maxedLink));

        Optional<String> result = shortLinkService.handleRedirect("MAXED");

        assertTrue(result.isEmpty());
        assertFalse(maxedLink.isActive());

        verify(notificationService, times(1)).sendNotification(eq(TEST_USER_ID), contains("Лимит переходов исчерпан"));
        verify(linkRepository, times(1)).save(maxedLink);
    }

    /**
     * Проверяет поведение при достижении лимита кликов в текущем переходе.
     * После инкремента счётчика лимит достигается, ссылка деактивируется,
     * и пользователю отправляется уведомление.
     */
    @Test
    void handleRedirect_SuccessAndDeactivate_MaxClicksReached_CurrentClick() {
        Link almostMaxedLink = Link.builder().code("ALMOST").originalUrl(TEST_URL).userId(TEST_USER_ID).maxClicks(5).expiresAt(NOW.plus(Duration.ofHours(1))).build();
        almostMaxedLink.getClicks().set(4);
        when(linkRepository.findByCode("ALMOST")).thenReturn(Optional.of(almostMaxedLink));

        Optional<String> result = shortLinkService.handleRedirect("ALMOST");

        assertTrue(result.isPresent());
        assertEquals(TEST_URL, result.get());
        assertEquals(5, almostMaxedLink.getClicks().get());
        assertFalse(almostMaxedLink.isActive());

        verify(notificationService, times(1)).sendNotification(eq(TEST_USER_ID), contains("Лимит переходов исчерпан"));
        verify(linkRepository, times(1)).save(almostMaxedLink);
    }
}