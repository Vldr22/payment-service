package org.resume.paymentservice.service.webhook.signature;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.exception.WebhookProcessingException;
import org.resume.paymentservice.properties.StripeProperties;
import org.springframework.stereotype.Component;

/**
 * Проверяет, что событие пришло от Stripe. Эндпоинт webhook открыт для всех,
 * поэтому подпись здесь единственная аутентификация.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeSignatureVerifier {

    private static final String UNKNOWN = "unknown";

    private final StripeProperties stripeProperties;

    public Event verifyWebhookEventSignature(String payload, String signatureHeader) {
        try {
            return Webhook.constructEvent(
                    payload,
                    signatureHeader,
                    stripeProperties.getWebhookSecret()
            );
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature");
            throw WebhookProcessingException.byInvalidSignature(UNKNOWN);
        }
    }
}
