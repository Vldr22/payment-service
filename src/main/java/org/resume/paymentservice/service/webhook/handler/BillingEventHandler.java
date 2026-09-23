package org.resume.paymentservice.service.webhook.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.contants.BillingConstants;
import org.resume.paymentservice.model.entity.BillingAttempt;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.model.enums.BillingAttemptStatus;
import org.resume.paymentservice.model.enums.PaymentStatus;
import org.resume.paymentservice.service.payment.PaymentService;
import org.resume.paymentservice.service.subscription.BillingAttemptService;
import org.resume.paymentservice.service.subscription.SubscriptionService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingEventHandler {

    private final SubscriptionService subscriptionService;
    private final BillingAttemptService billingAttemptService;
    private final PaymentService paymentService;

    /**
     * Закрывает попытку успехом и продлевает подписку.
     */
    public void handleBillingSucceeded(String stripePaymentIntentId, Long subscriptionId) {
        log.info("Processing billing succeeded: stripePaymentIntentId={}, subscriptionId={}",
                stripePaymentIntentId, subscriptionId);

        BillingAttempt attempt = billingAttemptService.findByStripePaymentIntentId(stripePaymentIntentId);

        if (isAlreadyClosed(attempt)) {
            return;
        }

        Subscription subscription = subscriptionService.findById(subscriptionId);
        Payment payment = paymentService.findByStripePaymentIntentId(stripePaymentIntentId);

        paymentService.updatePaymentStatus(stripePaymentIntentId, PaymentStatus.SUCCEEDED);
        billingAttemptService.markSucceeded(attempt, payment, stripePaymentIntentId);
        subscriptionService.markSucceeded(subscription, payment, attempt);
    }

    /**
     * Закрывает попытку неудачей и назначает ретрай.
     */
    public void handleBillingFailed(String stripePaymentIntentId, Long subscriptionId) {
        log.warn("Processing billing failed: stripePaymentIntentId={}, subscriptionId={}",
                stripePaymentIntentId, subscriptionId);

        BillingAttempt attempt = billingAttemptService.findByStripePaymentIntentId(stripePaymentIntentId);

        if (isAlreadyClosed(attempt)) {
            return;
        }

        Subscription subscription = subscriptionService.findById(subscriptionId);

        paymentService.updatePaymentStatus(stripePaymentIntentId, PaymentStatus.FAILED);
        billingAttemptService.markFailed(attempt, BillingConstants.PAYMENT_FAILED_MESSAGE);
        subscriptionService.markFailed(subscription, attempt);
    }

    private boolean isAlreadyClosed(BillingAttempt attempt) {
        if (attempt.getStatus() == BillingAttemptStatus.PENDING) {
            return false;
        }

        log.info("Billing attempt already closed, skipping: id={}, status={}",
                attempt.getId(), attempt.getStatus());
        return true;
    }
}
