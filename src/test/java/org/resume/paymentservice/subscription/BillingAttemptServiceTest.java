package org.resume.paymentservice.subscription;

import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.BillingAttempt;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.model.enums.BillingAttemptStatus;
import org.resume.paymentservice.repository.BillingAttemptRepository;
import org.resume.paymentservice.service.subscription.BillingAttemptService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingAttemptService — операции с попытками биллинга")
class BillingAttemptServiceTest {

    private static final String STRIPE_PAYMENT_ID      = "pi_existing_123";
    private static final String NONEXISTENT_STRIPE_ID  = "pi_nonexistent_999";
    private static final String ERROR_MESSAGE          = "Card declined";
    private static final LocalDateTime BILLING_PERIOD  = LocalDateTime.of(2026, 1, 1, 9, 0);

    @Mock
    private BillingAttemptRepository billingAttemptRepository;

    @InjectMocks
    private BillingAttemptService billingAttemptService;

    private BillingAttempt attempt;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        subscription = Instancio.create(Subscription.class);

        attempt = Instancio.of(BillingAttempt.class)
                .set(field(BillingAttempt::getStatus), BillingAttemptStatus.PENDING)
                .set(field(BillingAttempt::getSubscription), subscription)
                .create();
    }

    // findOrCreatePending
    /**
     * Проверяет создание новой попытки биллинга со статусом PENDING.
     * Номер попытки, подписка и период должны корректно сохраниться.
     */
    @Test
    void shouldCreatePendingAttempt_whenPeriodNotBilledYet() {
        when(billingAttemptRepository.findBySubscriptionIdAndBillingPeriod(subscription.getId(), BILLING_PERIOD))
                .thenReturn(Optional.empty());
        when(billingAttemptRepository.save(any(BillingAttempt.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BillingAttempt result = billingAttemptService.findOrCreatePending(subscription, 1, BILLING_PERIOD);

        assertThat(result.getStatus()).isEqualTo(BillingAttemptStatus.PENDING);
        assertThat(result.getSubscription()).isEqualTo(subscription);
        assertThat(result.getAttemptNumber()).isEqualTo(1);
        assertThat(result.getBillingPeriod()).isEqualTo(BILLING_PERIOD);
        verify(billingAttemptRepository).save(any(BillingAttempt.class));
    }

    /**
     * Повторный прогон за тот же период переиспользует существующую попытку,
     * а не создаёт новую — иначе подписка списалась бы дважды.
     */
    @Test
    void shouldReuseExistingAttempt_whenPeriodAlreadyStarted() {
        when(billingAttemptRepository.findBySubscriptionIdAndBillingPeriod(subscription.getId(), BILLING_PERIOD))
                .thenReturn(Optional.of(attempt));

        BillingAttempt result = billingAttemptService.findOrCreatePending(subscription, 1, BILLING_PERIOD);

        assertThat(result).isEqualTo(attempt);
        verify(billingAttemptRepository, never()).save(any(BillingAttempt.class));
    }

    // markSucceeded
    /**
     * После успешного списания попытка помечается как SUCCEEDED,
     * к ней привязывается платёж и Stripe ID.
     */
    @Test
    void shouldMarkAttemptSucceeded_withPaymentAndStripeId() {
        Payment payment = Instancio.create(Payment.class);

        billingAttemptService.markSucceeded(attempt, payment, STRIPE_PAYMENT_ID);

        assertThat(attempt.getStatus()).isEqualTo(BillingAttemptStatus.SUCCEEDED);
        assertThat(attempt.getPayment()).isEqualTo(payment);
        assertThat(attempt.getStripePaymentIntentId()).isEqualTo(STRIPE_PAYMENT_ID);
        assertThat(attempt.getExecutedAt()).isNotNull();
        verify(billingAttemptRepository).save(attempt);
    }

    // markFailed
    /**
     * При неудачном списании попытка помечается как FAILED
     * с сообщением об ошибке и временем выполнения.
     */
    @Test
    void shouldMarkAttemptFailed_withErrorMessage() {
        billingAttemptService.markFailed(attempt, ERROR_MESSAGE);

        assertThat(attempt.getStatus()).isEqualTo(BillingAttemptStatus.FAILED);
        assertThat(attempt.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
        assertThat(attempt.getExecutedAt()).isNotNull();
        verify(billingAttemptRepository).save(attempt);
    }

    // findByStripePaymentIntentId
    /** Возвращает попытку биллинга по-существующему Stripe ID. */
    @Test
    void shouldReturnAttempt_whenStripeIdExists() {
        when(billingAttemptRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID))
                .thenReturn(Optional.of(attempt));

        BillingAttempt result = billingAttemptService.findByStripePaymentIntentId(STRIPE_PAYMENT_ID);

        assertThat(result).isEqualTo(attempt);
    }

    /** Бросает NotFoundException если попытка с таким Stripe ID не найдена. */
    @Test
    void shouldThrowNotFound_whenStripeIdNotExist() {
        when(billingAttemptRepository.findByStripePaymentIntentId(NONEXISTENT_STRIPE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingAttemptService.findByStripePaymentIntentId(NONEXISTENT_STRIPE_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // findAllBySubscriptionId
    /**
     * Возвращает все попытки биллинга для конкретной подписки.
     * Используется для отображения истории биллинга клиенту.
     */
    @Test
    void shouldReturnAllAttempts_forSubscription() {
        List<BillingAttempt> attempts = List.of(attempt, Instancio.create(BillingAttempt.class));
        when(billingAttemptRepository.findAllBySubscriptionId(subscription.getId()))
                .thenReturn(attempts);

        List<BillingAttempt> result = billingAttemptService.findAllBySubscriptionId(subscription.getId());

        assertThat(result).hasSize(2).isEqualTo(attempts);
    }

    // save
    /** Проверяет что save делегирует сохранение в репозиторий. */
    @Test
    void shouldSaveAttempt() {
        billingAttemptService.save(attempt);

        verify(billingAttemptRepository).save(attempt);
    }
}
