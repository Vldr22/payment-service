package org.resume.paymentservice.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.BillingAttempt;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.model.enums.BillingAttemptStatus;
import org.resume.paymentservice.repository.BillingAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAttemptService {

    private final BillingAttemptRepository billingAttemptRepository;

    /**
     * Возвращает попытку за период, создавая её при первом обращении.
     * Повторный прогон после сбоя переиспользует существующую.
     */
    @Transactional
    public BillingAttempt findOrCreatePending(Subscription subscription, int attemptNumber,
                                              LocalDateTime billingPeriod) {
        return billingAttemptRepository
                .findBySubscriptionIdAndBillingPeriod(subscription.getId(), billingPeriod)
                .orElseGet(() -> createPending(subscription, attemptNumber, billingPeriod));
    }

    @Transactional
    public void markSucceeded(BillingAttempt attempt, Payment payment, String stripePaymentIntentId) {
        attempt.setStatus(BillingAttemptStatus.SUCCEEDED);
        attempt.setPayment(payment);
        attempt.setStripePaymentIntentId(stripePaymentIntentId);
        attempt.setExecutedAt(LocalDateTime.now());
        billingAttemptRepository.save(attempt);

        log.info("BillingAttempt succeeded: id={}, stripePaymentIntentId={}",
                attempt.getId(), stripePaymentIntentId);
    }

    @Transactional
    public void markFailed(BillingAttempt attempt, String errorMessage) {
        attempt.setStatus(BillingAttemptStatus.FAILED);
        attempt.setErrorMessage(errorMessage);
        attempt.setExecutedAt(LocalDateTime.now());
        billingAttemptRepository.save(attempt);

        log.warn("BillingAttempt failed: id={}", attempt.getId());
    }

    @Transactional(readOnly = true)
    public BillingAttempt findByStripePaymentIntentId(String stripePaymentIntentId) {
        return billingAttemptRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> NotFoundException.billingAttemptByPaymentIntentId(stripePaymentIntentId));
    }

    @Transactional(readOnly = true)
    public List<BillingAttempt> findAllBySubscriptionId(Long subscriptionId) {
        return billingAttemptRepository.findAllBySubscriptionId(subscriptionId);
    }

    @Transactional
    public void save(BillingAttempt attempt) {
        billingAttemptRepository.save(attempt);
    }

    private BillingAttempt createPending(Subscription subscription, int attemptNumber,
                                         LocalDateTime billingPeriod) {
        BillingAttempt attempt = new BillingAttempt(subscription, attemptNumber, billingPeriod);
        BillingAttempt saved = billingAttemptRepository.save(attempt);

        log.info("BillingAttempt created: id={}, subscriptionId={}, attempt#{}, period={}",
                saved.getId(), subscription.getId(), attemptNumber, billingPeriod);
        return saved;
    }

}
