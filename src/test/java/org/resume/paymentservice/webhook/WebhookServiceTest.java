package org.resume.paymentservice.webhook;

import com.stripe.model.Event;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.WebhookEvent;
import org.resume.paymentservice.repository.WebhookEventRepository;
import org.resume.paymentservice.service.webhook.WebhookEventHandlerRegistry;
import org.resume.paymentservice.service.webhook.WebhookService;
import org.resume.paymentservice.service.webhook.signature.WebhookSignatureVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookService — обработка входящих webhook событий от Stripe")
class WebhookServiceTest {

    private static final String PAYLOAD = "stripe_payload";
    private static final String SIGNATURE_HEADER = "stripe_signature_header";
    private static final String EVENT_ID = "evt_test_123";
    private static final String SUPPORTED_TYPE = "payment_intent.succeeded";
    private static final String UNSUPPORTED_TYPE = "customer.created";

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookSignatureVerifier webhookSignatureVerifier;

    @Mock
    private WebhookEventHandlerRegistry webhookEventHandlerRegistry;

    @InjectMocks
    private WebhookService webhookService;

    private Event event;

    @BeforeEach
    void setUp() {
        event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(webhookSignatureVerifier.verifyWebhookEventSignature(PAYLOAD, SIGNATURE_HEADER))
                .thenReturn(event);
    }

    // createWebhookEvent — успешная обработка

    /**
     * Проверяет полный happy path: событие проходит верификацию подписи,
     * сохраняется в БД, передается обработчику и помечается как обработанное.
     */
    @Test
    void shouldProcessWebhookEvent_whenValidAndSupported() {
        WebhookEvent webhookEvent = Instancio.create(WebhookEvent.class);

        when(event.getType()).thenReturn(SUPPORTED_TYPE);
        when(webhookEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);
        when(webhookEventRepository.findByEventId(EVENT_ID)).thenReturn(Optional.of(webhookEvent));

        webhookService.createWebhookEvent(PAYLOAD, SIGNATURE_HEADER);

        verify(webhookEventHandlerRegistry).dispatch(event);
        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
    }

    /**
     * Неподдерживаемые типы событий игнорируются — не сохраняются и не передается обработчику.
     * Это нормальное поведение: Stripe шлёт много разных событий.
     */
    @Test
    void shouldIgnoreEvent_whenTypeNotSupported() {
        when(event.getType()).thenReturn(UNSUPPORTED_TYPE);
        when(webhookEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);

        webhookService.createWebhookEvent(PAYLOAD, SIGNATURE_HEADER);

        verify(webhookEventRepository, never()).save(any());
        verify(webhookEventHandlerRegistry, never()).dispatch(any());
    }

    // createWebhookEvent — ошибки обработки

    /**
     * Если событие с таким ID уже обработано — выходим без ошибки и повторно не обрабатываем:
     * ответ об ошибке заставил бы Stripe повторять доставку трое суток.
     */
    @Test
    void shouldIgnoreDuplicateEvent_withoutReprocessing() {
        when(webhookEventRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        assertThatNoException().isThrownBy(() -> webhookService.createWebhookEvent(PAYLOAD, SIGNATURE_HEADER));

        verify(webhookEventHandlerRegistry, never()).dispatch(any());
        verify(webhookEventRepository, never()).save(any());
    }

    /**
     * Бросает NotFoundException если webhook событие не найдено в БД
     * при попытке пометить его как обработанное.
     */
    @Test
    void shouldThrowNotFound_whenWebhookEventNotFoundOnMarkProcessed() {
        when(event.getType()).thenReturn(SUPPORTED_TYPE);
        when(webhookEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);
        when(webhookEventRepository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.createWebhookEvent(PAYLOAD, SIGNATURE_HEADER))
                .isInstanceOf(NotFoundException.class);

        verify(webhookEventRepository, never()).save(argThat(e -> e != null && e.isProcessed()));
    }
}
