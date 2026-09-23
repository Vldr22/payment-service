package org.resume.paymentservice.model.enums;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    REFUNDED;

    public static PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentStatus.PENDING;
            case "processing" -> PaymentStatus.PROCESSING;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELED;
            default -> PaymentStatus.FAILED;
        };
    }

    /**
     * Проверяет, допустим ли переход в целевой статус.
     */
    public boolean isPossibleTransitionTo(PaymentStatus target) {
        if (target == this) {
            return false;
        }

        return switch (this) {
            case PENDING, PROCESSING, FAILED -> target != REFUNDED;
            case SUCCEEDED -> target == REFUNDED;
            case CANCELED, REFUNDED -> false;
        };
    }
}