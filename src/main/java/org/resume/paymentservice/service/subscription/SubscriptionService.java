package org.resume.paymentservice.service.subscription;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.exception.AlreadyExistsException;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.BillingAttempt;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.SavedCard;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.model.enums.SubscriptionStatus;
import org.resume.paymentservice.model.enums.SubscriptionType;
import org.resume.paymentservice.properties.BillingProperties;
import org.resume.paymentservice.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final BillingProperties billingProperties;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public Subscription create(User user, SavedCard savedCard, SubscriptionType type) {
        Optional<Subscription> existing = subscriptionRepository.findByUserId(user.getId());

        if (existing.isPresent()) {
            return handleExistingSubscription(existing.get(), savedCard, type);
        }

        return createNewSubscription(user, savedCard, type);
    }

    @Transactional(readOnly = true)
    public Subscription findById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.subscriptionById(id));
    }

    @Transactional(readOnly = true)
    public Subscription findByUserId(Long userId) {
        return subscriptionRepository.findByUserIdAndSubscriptionStatusNot(userId, SubscriptionStatus.CANCELLED)
                .orElseThrow(() -> NotFoundException.subscriptionByUserId(userId));
    }

    @Transactional(readOnly = true)
    public List<Subscription> findDueSubscriptions() {
        return subscriptionRepository.findDueSubscriptions(
                LocalDateTime.now(),
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE)
        );
    }

    /**
     * Продлевает подписку от периода попытки.
     */
    @Transactional
    public void markSucceeded(Subscription subscription, Payment payment, BillingAttempt attempt) {
        LocalDateTime nextBillingDate = attempt.getBillingPeriod()
                .plusDays(billingProperties.getIntervalDays());

        subscription.setLastPayment(payment);
        subscription.setRetryCount(0);
        subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextBillingDate(nextBillingDate);
        subscription.setEndDate(nextBillingDate);
        subscriptionRepository.save(subscription);

        log.info("Subscription renewed: id={}, nextBillingDate={}",
                subscription.getId(), nextBillingDate);
    }

    /**
     * Назначает ретрай или приостанавливает подписку.
     */
    @Transactional
    public void markFailed(Subscription subscription, BillingAttempt attempt) {
        subscription.setRetryCount(attempt.getAttemptNumber());

        if (isRetryLimitReached(attempt)) {
            suspend(subscription);
        } else {
            scheduleRetry(subscription, attempt);
        }

        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void markProcessing(Subscription subscription) {
        subscription.setSubscriptionStatus(SubscriptionStatus.PROCESSING);
        subscriptionRepository.save(subscription);
        log.info("Subscription marked PROCESSING: id={}", subscription.getId());
    }

    @Transactional
    public void cancel(Subscription subscription) {
        subscription.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);
        log.info("Subscription cancelled: id={}", subscription.getId());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSubscriptionByCard(Long cardId) {
        return subscriptionRepository.existsBySavedCardIdAndSubscriptionStatusIn(
                cardId,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE)
        );
    }

    //Helpers Methods
    private boolean isRetryLimitReached(BillingAttempt attempt) {
        return attempt.getAttemptNumber() >= billingProperties.getMaxRetryCount();
    }

    private void suspend(Subscription subscription) {
        subscription.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
        log.warn("Subscription suspended after {} retries: id={}",
                billingProperties.getMaxRetryCount(), subscription.getId());
    }

    private void scheduleRetry(Subscription subscription, BillingAttempt attempt) {
        subscription.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        subscription.setNextBillingDate(attempt.getBillingPeriod()
                .plusDays(billingProperties.getRetryIntervalDays()));

        log.warn("Subscription marked PAST_DUE: id={}, retryCount={}",
                subscription.getId(), attempt.getAttemptNumber());
    }

    private BigDecimal resolveAmount(SubscriptionType type) {
        return switch (type) {
            case BASIC -> billingProperties.getBasicAmount();
            case PREMIUM -> billingProperties.getPremiumAmount();
        };
    }

    private Subscription handleExistingSubscription(Subscription subscription,
                                                    SavedCard savedCard, SubscriptionType type) {
        if (isActiveOrPastDue(subscription)) {
            throw AlreadyExistsException.subscriptionByUserId(subscription.getUser().getId());
        }
        return reactivate(subscription, savedCard, type);
    }

    private boolean isActiveOrPastDue(Subscription subscription) {
        return subscription.getSubscriptionStatus() == SubscriptionStatus.ACTIVE ||
                subscription.getSubscriptionStatus() == SubscriptionStatus.PAST_DUE;
    }

    private Subscription createNewSubscription(User user, SavedCard savedCard, SubscriptionType type) {
        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = new Subscription(
                user, savedCard, type,
                resolveAmount(type),
                billingProperties.getDefaultCurrency(),
                billingProperties.getIntervalDays()
        );

        subscription.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        subscription.setNextBillingDate(now);
        subscription.setEndDate(now.plusDays(billingProperties.getIntervalDays()));

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created: id={}, userId={}, type={}",
                saved.getId(), user.getId(), type);
        return saved;
    }

    private Subscription reactivate(Subscription subscription, SavedCard savedCard,
                                    SubscriptionType type) {
        LocalDateTime now = LocalDateTime.now();
        subscription.setSavedCard(savedCard);
        subscription.setSubscriptionType(type);
        subscription.setAmount(resolveAmount(type));
        subscription.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        subscription.setRetryCount(0);
        subscription.setEndDate(now.plusDays(billingProperties.getIntervalDays()));
        subscription.setNextBillingDate(now);

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription reactivated: id={}, userId={}, type={}",
                saved.getId(), subscription.getUser().getId(), type);
        return saved;
    }
}
