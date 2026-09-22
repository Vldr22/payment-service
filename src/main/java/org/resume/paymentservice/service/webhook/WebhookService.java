package org.resume.paymentservice.service.webhook;


import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.WebhookEvent;
import org.resume.paymentservice.repository.WebhookEventRepository;
import org.resume.paymentservice.service.webhook.signature.StripeSignatureVerifier;
import org.springframework.stereotype.Service;
import com.stripe.model.Event;

import static org.resume.paymentservice.utils.StripeEventTypes.SUPPORTED_EVENT_TYPES;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final StripeSignatureVerifier stripeSignatureVerifier;
    private final WebhookEventHandlerRegistry webhookEventHandlerRegistry;

    /**
     * Обрабатывает событие Stripe. Повторная доставка уже обработанного события
     * завершается без ошибки: для Stripe это штатная ситуация, и ответ об ошибке
     * заставил бы его повторять доставку трое суток.
     */
    @Transactional
    public void createWebhookEvent(String payload, String signatureHeader) {
        Event event = stripeSignatureVerifier.verifyWebhookEventSignature(payload, signatureHeader);

        if (isAlreadyProcessed(event.getId())) {
            return;
        }

        if (!SUPPORTED_EVENT_TYPES.contains(event.getType())) {
            log.warn("Ignoring unsupported event type: {}", event.getType());
            return;
        }

        saveWebhookEventInDatabase(event, payload);
        webhookEventHandlerRegistry.dispatch(event);
        markEventAsProcessed(event.getId());

        log.info("Webhook processed successfully: eventId={}", event.getId());
    }

    private boolean isAlreadyProcessed(String eventId) {
        boolean processed = webhookEventRepository.existsByEventId(eventId);
        if (processed) {
            log.info("Webhook event already processed, skipping: {}", eventId);
        }
        return processed;
    }

    private void saveWebhookEventInDatabase(Event event, String payload) {
        WebhookEvent webhookEvent = new WebhookEvent(
                event.getId(),
                event.getType(),
                false,
                payload
        );

        webhookEventRepository.save(webhookEvent);
        log.info("Saved webhook event in database: eventId={}, eventType={}", event.getId(), event.getType());
    }


    private void markEventAsProcessed(String eventId) {
        WebhookEvent webhookEvent = webhookEventRepository.findByEventId(eventId)
                .orElseThrow(() -> NotFoundException.webhookEventByID(eventId));

        webhookEvent.setProcessed(true);
        webhookEventRepository.save(webhookEvent);

        log.info("Webhook event marked as processed: {}", eventId);
    }

}
