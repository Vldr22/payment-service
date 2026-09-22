package org.resume.paymentservice.exception;

import lombok.Getter;
import org.resume.paymentservice.utils.ErrorMessages;

@Getter
public class WebhookProcessingException extends RuntimeException {

    private final String eventId;

    private WebhookProcessingException(String message, String eventId) {
        super(message);
        this.eventId = eventId;
    }

    public static WebhookProcessingException byInvalidSignature(String eventId) {
        return new WebhookProcessingException(
                String.format("%s%s", ErrorMessages.WEBHOOK_INVALID_SIGNATURE, eventId),
                eventId
        );
    }

    public static WebhookProcessingException byDeserializationFailed(String eventId) {
        return new WebhookProcessingException(
                String.format("%s%s", ErrorMessages.WEBHOOK_DESERIALIZATION_FAILED, eventId),
                eventId
        );
    }

}
