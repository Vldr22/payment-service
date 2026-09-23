package org.resume.paymentservice.service.sheduler;

import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.contants.BillingConstants;
import org.resume.paymentservice.model.dto.data.BillingChargeData;
import org.resume.paymentservice.model.entity.BillingAttempt;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.service.payment.PaymentService;
import org.resume.paymentservice.service.payment.StripeService;
import org.resume.paymentservice.service.subscription.BillingAttemptService;
import org.resume.paymentservice.service.subscription.SubscriptionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingOrchestrator {

    private final SubscriptionService subscriptionService;
    private final BillingAttemptService billingAttemptService;
    private final StripeService stripeService;
    private final PaymentService paymentService;

    /**
     * Списывает по подписке за текущий период.
     * Подписка выходит из выборки планировщика до обращения к Stripe, а после успешного
     * списания попытка не может быть помечена неудачной.
     */
    public void processSubscription(Subscription subscription) {
        log.info("Processing subscription: id={}, userId={}",
                subscription.getId(), subscription.getUser().getId());

        LocalDateTime billingPeriod = subscription.getNextBillingDate();
        BillingAttempt attempt = startAttempt(subscription, billingPeriod);

        try {
            PaymentIntent paymentIntent = stripeService.chargeWithSavedCard(
                    buildChargeData(subscription, billingPeriod));
            recordCharge(subscription, attempt, paymentIntent);
        } catch (Exception e) {
            failAttempt(subscription, attempt, e);
        }
    }

    private BillingAttempt startAttempt(Subscription subscription, LocalDateTime billingPeriod) {
        int attemptNumber = subscription.getRetryCount() + 1;
        BillingAttempt attempt = billingAttemptService.findOrCreatePending(
                subscription, attemptNumber, billingPeriod);

        subscriptionService.markProcessing(subscription);
        return attempt;
    }

    /**
     * Фиксирует состоявшееся списание. Ошибки записи не выбрасывает: деньги уже списаны,
     * и пометить попытку неудачной на этом этапе нельзя.
     */
    private void recordCharge(Subscription subscription, BillingAttempt attempt, PaymentIntent paymentIntent) {
        try {
            attempt.setStripePaymentIntentId(paymentIntent.getId());
            billingAttemptService.save(attempt);
            paymentService.saveBillingPayment(paymentIntent, subscription);

            log.info("Charge initiated: subscriptionId={}, attemptId={}",
                    subscription.getId(), attempt.getId());
        } catch (Exception e) {
            log.error("Charge succeeded but not recorded: subscriptionId={}, stripePaymentIntentId={}",
                    subscription.getId(), paymentIntent.getId(), e);
        }
    }

    private void failAttempt(Subscription subscription, BillingAttempt attempt, Exception e) {
        log.error("Charge failed: subscriptionId={}, error={}", subscription.getId(), e.getMessage());
        billingAttemptService.markFailed(attempt, e.getMessage());
        subscriptionService.markFailed(subscription, attempt);
    }

    private BillingChargeData buildChargeData(Subscription subscription, LocalDateTime billingPeriod) {
        return BillingChargeData.builder()
                .amount(subscription.getAmount())
                .currency(subscription.getCurrency())
                .customerId(subscription.getUser().getStripeCustomerId())
                .paymentMethodId(subscription.getSavedCard().getStripePaymentMethodId())
                .description(subscription.getSubscriptionType().name())
                .subscriptionId(subscription.getId())
                .idempotencyKey(String.format(
                        BillingConstants.IDEMPOTENCY_KEY_FORMAT, subscription.getId(), billingPeriod))
                .build();
    }
}
