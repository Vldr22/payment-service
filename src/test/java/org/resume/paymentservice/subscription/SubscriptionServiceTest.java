package org.resume.paymentservice.subscription;

import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.exception.AlreadyExistsException;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.entity.BillingAttempt;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.SavedCard;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.model.enums.Currency;
import org.resume.paymentservice.model.enums.SubscriptionStatus;
import org.resume.paymentservice.model.enums.SubscriptionType;
import org.resume.paymentservice.properties.BillingProperties;
import org.resume.paymentservice.repository.SubscriptionRepository;
import org.resume.paymentservice.service.subscription.SubscriptionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService — управление подписками и биллинг")
class SubscriptionServiceTest {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_INTERVAL = 3;
    private static final int INTERVAL_DAYS = 30;
    private static final BigDecimal BASIC_PRICE = new BigDecimal("9.99");
    private static final BigDecimal PREMIUM_PRICE = new BigDecimal("19.99");
    private static final LocalDateTime BILLING_PERIOD = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private BillingProperties billingProperties;

    private SubscriptionService subscriptionService;

    private User user;
    private SavedCard savedCard;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        billingProperties = new BillingProperties(
                MAX_RETRY_COUNT,
                RETRY_INTERVAL,
                INTERVAL_DAYS,
                BASIC_PRICE,
                PREMIUM_PRICE,
                Currency.USD
        );

        subscriptionService = new SubscriptionService(billingProperties, subscriptionRepository);

        user = Instancio.create(User.class);
        savedCard = Instancio.create(SavedCard.class);

        subscription = Instancio.of(Subscription.class)
                .set(field(Subscription::getUser), user)
                .set(field(Subscription::getRetryCount), 0)
                .set(field(Subscription::getSubscriptionStatus), SubscriptionStatus.ACTIVE)
                .create();
    }

    // findById
    /**
     * Возвращает подписку по-существующему ID.
     */
    @Test
    void shouldReturnSubscription_whenIdExists() {
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));

        Subscription result = subscriptionService.findById(subscription.getId());

        assertThat(result).isEqualTo(subscription);
    }

    /**
     * Бросает NotFoundException если подписка с таким ID не найдена.
     */
    @Test
    void shouldThrowNotFound_whenSubscriptionIdNotExist() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    //findByUserId
    /**
     * Возвращает активную подписку пользователя (не CANCELLED).
     */
    @Test
    void shouldReturnSubscription_whenUserHasActiveSubscription() {
        when(subscriptionRepository.findByUserIdAndSubscriptionStatusNot(user.getId(), SubscriptionStatus.CANCELLED))
                .thenReturn(Optional.of(subscription));

        Subscription result = subscriptionService.findByUserId(user.getId());

        assertThat(result).isEqualTo(subscription);
    }

    /**
     * Бросает NotFoundException если у пользователя нет активной подписки.
     */
    @Test
    void shouldThrowNotFound_whenUserHasNoActiveSubscription() {
        when(subscriptionRepository.findByUserIdAndSubscriptionStatusNot(user.getId(), SubscriptionStatus.CANCELLED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.findByUserId(user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    // findDueSubscriptions
    /**
     * Возвращает список подписок у которых подошла дата биллинга.
     */
    @Test
    void shouldReturnDueSubscriptions() {
        List<Subscription> due = List.of(subscription);
        when(subscriptionRepository.findDueSubscriptions(any(), any())).thenReturn(due);

        List<Subscription> result = subscriptionService.findDueSubscriptions();

        assertThat(result).hasSize(1).isEqualTo(due);
    }

    // hasActiveSubscriptionByCard
    /**
     * Возвращает true если к карте привязана активная или просроченная подписка.
     * Используется перед удалением карты — нельзя удалить карту с активной подпиской.
     */
    @Test
    void shouldReturnTrue_whenCardHasActiveSubscription() {
        when(subscriptionRepository.existsBySavedCardIdAndSubscriptionStatusIn(any(), any()))
                .thenReturn(true);

        boolean result = subscriptionService.hasActiveSubscriptionByCard(savedCard.getId());

        assertThat(result).isTrue();
    }

    // isActiveOrPastDue — непокрытая ветка PAST_DUE
    /**
     * Нельзя создать подписку если предыдущая в статусе PAST_DUE.
     */
    @Test
    void shouldThrowAlreadyExists_whenPastDueSubscriptionExists() {
        subscription.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> subscriptionService.create(user, savedCard, SubscriptionType.BASIC))
                .isInstanceOf(AlreadyExistsException.class);
    }

    // markFailed
    /**
     * Ключевой тест биллинга: если номер попытки не достиг максимума —
     * подписка переходит в PAST_DUE, ретрай назначается от периода попытки.
     */
    @Test
    void shouldMarkPastDue_whenRetryCountBelowMax() {
        BillingAttempt attempt = billingAttempt(1);

        subscriptionService.markFailed(subscription, attempt);

        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getRetryCount()).isEqualTo(1);
        assertThat(subscription.getNextBillingDate()).isEqualTo(BILLING_PERIOD.plusDays(RETRY_INTERVAL));
        verify(subscriptionRepository).save(subscription);
    }

    /**
     * Если исчерпаны все попытки (номер попытки достиг maxRetryCount) —
     * подписка переходит в SUSPENDED и больше не будет попыток списания.
     */
    @Test
    void shouldSuspend_whenRetryCountReachesMax() {
        BillingAttempt attempt = billingAttempt(MAX_RETRY_COUNT);

        subscriptionService.markFailed(subscription, attempt);

        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(subscription.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);
        verify(subscriptionRepository).save(subscription);
    }

    // markSucceeded
    /**
     * После успешного списания подписка возвращается в ACTIVE,
     * retryCount сбрасывается, период считается от якоря попытки.
     */
    @Test
    void shouldMarkActive_andResetRetryCount_whenPaymentSucceeded() {
        Payment payment = Instancio.create(Payment.class);
        BillingAttempt attempt = billingAttempt(1);
        subscription.setRetryCount(2);

        subscriptionService.markSucceeded(subscription, payment, attempt);

        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getRetryCount()).isEqualTo(0);
        assertThat(subscription.getLastPayment()).isEqualTo(payment);
        assertThat(subscription.getNextBillingDate()).isEqualTo(BILLING_PERIOD.plusDays(INTERVAL_DAYS));
        assertThat(subscription.getEndDate()).isEqualTo(BILLING_PERIOD.plusDays(INTERVAL_DAYS));
        verify(subscriptionRepository).save(subscription);
    }

    /**
     * Повторное продление по той же попытке не сдвигает дату ещё на период.
     */
    @Test
    void shouldKeepSameBillingDate_whenRenewedTwice() {
        Payment payment = Instancio.create(Payment.class);
        BillingAttempt attempt = billingAttempt(1);

        subscriptionService.markSucceeded(subscription, payment, attempt);
        LocalDateTime afterFirstRenewal = subscription.getNextBillingDate();
        subscriptionService.markSucceeded(subscription, payment, attempt);

        assertThat(subscription.getNextBillingDate()).isEqualTo(afterFirstRenewal);
    }

    // cancel
    /**
     * Отмена подписки устанавливает статус CANCELLED и сохраняет изменения.
     */
    @Test
    void shouldCancelSubscription() {
        subscriptionService.cancel(subscription);

        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(subscriptionRepository).save(subscription);
    }

    // create

    /**
     * Проверяет создание новой подписки когда у пользователя её ещё нет.
     * Новая подписка стартует в статусе PAST_DUE — первое списание произойдёт при ближайшем биллинге.
     */
    @Test
    void shouldCreateNewSubscription_whenUserHasNoExisting() {
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = subscriptionService.create(user, savedCard, SubscriptionType.BASIC);

        assertThat(result.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(result.getAmount()).isEqualTo(billingProperties.getBasicAmount());
        verify(subscriptionRepository).save(any());
    }

    /**
     * Нельзя создать вторую подписку если первая ещё активна.
     * Защита от дублирования подписок.
     */
    @Test
    void shouldThrowAlreadyExists_whenActiveSubscriptionExists() {
        subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> subscriptionService.create(user, savedCard, SubscriptionType.BASIC))
                .isInstanceOf(AlreadyExistsException.class);
    }

    /**
     * Если подписка была отменена — можно создать новую (реактивация).
     * Статус сбрасывается в PAST_DUE, карта и тип обновляются.
     */
    @Test
    void shouldReactivateSubscription_whenPreviousWasCancelled() {
        subscription.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = subscriptionService.create(user, savedCard, SubscriptionType.PREMIUM);

        assertThat(result.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(result.getSavedCard()).isEqualTo(savedCard);
        assertThat(result.getSubscriptionType()).isEqualTo(SubscriptionType.PREMIUM);
        assertThat(result.getRetryCount()).isEqualTo(0);
    }

    private BillingAttempt billingAttempt(int attemptNumber) {
        return Instancio.of(BillingAttempt.class)
                .set(field(BillingAttempt::getAttemptNumber), attemptNumber)
                .set(field(BillingAttempt::getBillingPeriod), BILLING_PERIOD)
                .create();
    }
}
