package org.resume.paymentservice.facade;

import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.exception.StripePaymentException;
import org.resume.paymentservice.model.dto.request.ConfirmPaymentRequest;
import org.resume.paymentservice.model.dto.request.ConfirmWithSavedCardRequest;
import org.resume.paymentservice.model.dto.request.CreatePaymentRequest;
import org.resume.paymentservice.model.dto.request.RefundRequest;
import org.resume.paymentservice.model.dto.response.PaymentResponse;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.Refund;
import org.resume.paymentservice.model.entity.SavedCard;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.model.enums.PaymentStatus;
import org.resume.paymentservice.service.card.SavedCardService;
import org.resume.paymentservice.service.facade.PaymentFacadeService;
import org.resume.paymentservice.service.payment.PaymentService;
import org.resume.paymentservice.service.payment.RefundService;
import org.resume.paymentservice.service.payment.StripeService;
import org.resume.paymentservice.service.user.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentFacadeService — оркестрация платежей")
class PaymentFacadeServiceTest {

    private static final String STRIPE_PAYMENT_ID = "pi_existing_123";

    @Mock
    private PaymentService paymentService;

    @Mock
    private StripeService stripeService;

    @Mock
    private UserService userService;

    @Mock
    private RefundService refundService;

    @Mock
    private SavedCardService savedCardService;

    @InjectMocks
    private PaymentFacadeService paymentFacadeService;

    private User currentUser;
    private Payment payment;
    private PaymentResponse stripeSuccessResponse;

    @BeforeEach
    void setUp() {
        currentUser = Instancio.create(User.class);

        payment = Instancio.of(Payment.class)
                .set(field(Payment::getUser), currentUser)
                .set(field(Payment::getStripePaymentIntentId), STRIPE_PAYMENT_ID)
                .set(field(Payment::getStatus), PaymentStatus.PENDING)
                .create();

        stripeSuccessResponse = PaymentResponse.builder()
                .id(STRIPE_PAYMENT_ID)
                .status("succeeded")
                .amount(100L)
                .currency("USD")
                .build();
    }

    // createPayment

    /**
     * Проверяет что при создании платежа вызывается Stripe,
     * результат сохраняется в БД и возвращается ответ от Stripe.
     */
    @Test
    void shouldCreatePayment_andSaveToDb() {
        CreatePaymentRequest request = Instancio.create(CreatePaymentRequest.class);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(stripeService.createStripePayment(eq(request), any())).thenReturn(stripeSuccessResponse);
        when(paymentService.savePayment(any())).thenReturn(payment);

        PaymentResponse result = paymentFacadeService.createPayment(request);

        assertThat(result).isEqualTo(stripeSuccessResponse);
        verify(paymentService).savePayment(any());
        verify(stripeService).createStripePayment(eq(request), any());
    }

    // createRefund

    /**
     * Проверяет что запрос на возврат создаётся для платежа текущего пользователя
     * и возвращается корректный RefundResponse.
     */
    @Test
    void shouldCreateRefund_forCurrentUserPayment() {
        RefundRequest request = Instancio.create(RefundRequest.class);
        Refund refund = Instancio.of(Refund.class)
                .set(field(Refund::getPayment), payment)
                .create();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(paymentService.findByStripePaymentIntentIdAndUser(STRIPE_PAYMENT_ID, currentUser))
                .thenReturn(payment);
        when(refundService.createRefundRequest(eq(payment), eq(currentUser), any()))
                .thenReturn(refund);

        var result = paymentFacadeService.createRefund(STRIPE_PAYMENT_ID, request);

        assertThat(result).isNotNull();
        verify(refundService).createRefundRequest(eq(payment), eq(currentUser), any());
    }

    // confirmPayment

    /**
     * Проверяет успешное подтверждение платежа: статус обновляется согласно ответу от Stripe.
     */
    @Test
    void shouldConfirmPayment_andUpdateStatus() {
        ConfirmPaymentRequest request = Instancio.create(ConfirmPaymentRequest.class);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(paymentService.findByStripePaymentIntentIdAndUser(STRIPE_PAYMENT_ID, currentUser))
                .thenReturn(payment);
        when(stripeService.confirmPayment(any(), any(), any())).thenReturn(stripeSuccessResponse);

        PaymentResponse result = paymentFacadeService.confirmPayment(STRIPE_PAYMENT_ID, request);

        assertThat(result).isEqualTo(stripeSuccessResponse);
        verify(paymentService).updatePaymentStatus(STRIPE_PAYMENT_ID, PaymentStatus.SUCCEEDED);
    }

    /**
     * Если Stripe бросает исключение при подтверждении, —
     * платёж помечается как FAILED и исключение прокидывается дальше.
     */
    @Test
    void shouldMarkPaymentFailed_whenStripeThrowsOnConfirm() {
        ConfirmPaymentRequest request = Instancio.create(ConfirmPaymentRequest.class);
        StripePaymentException stripeEx = mock(StripePaymentException.class);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(paymentService.findByStripePaymentIntentIdAndUser(STRIPE_PAYMENT_ID, currentUser))
                .thenReturn(payment);
        when(stripeService.confirmPayment(any(), any(), any())).thenThrow(stripeEx);

        assertThatThrownBy(() -> paymentFacadeService.confirmPayment(STRIPE_PAYMENT_ID, request))
                .isEqualTo(stripeEx);

        verify(paymentService).updatePaymentStatus(STRIPE_PAYMENT_ID, PaymentStatus.FAILED);
    }

    // confirmPaymentWithSavedCard

    /**
     * Проверяет подтверждение через сохранённую карту:
     * статус обновляется и карта привязывается к платежу.
     */
    @Test
    void shouldConfirmWithSavedCard_andAttachCard() {
        SavedCard card = Instancio.create(SavedCard.class);
        ConfirmWithSavedCardRequest request = new ConfirmWithSavedCardRequest(card.getId(), "https://return.url");

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(paymentService.findByStripePaymentIntentIdAndUser(STRIPE_PAYMENT_ID, currentUser))
                .thenReturn(payment);
        when(savedCardService.getCardByIdAndUser(card.getId(), currentUser)).thenReturn(card);
        when(stripeService.confirmPayment(any(), any(), any())).thenReturn(stripeSuccessResponse);

        PaymentResponse result = paymentFacadeService.confirmPaymentWithSavedCard(STRIPE_PAYMENT_ID, request);

        assertThat(result).isEqualTo(stripeSuccessResponse);
        verify(paymentService).updatePaymentStatus(STRIPE_PAYMENT_ID, PaymentStatus.SUCCEEDED);
        verify(paymentService).updateSavedCard(STRIPE_PAYMENT_ID, card);
    }

    /**
     * Если Stripe падает при оплате сохранённой картой, —
     * платёж помечается FAILED, исключение прокидывается.
     */
    @Test
    void shouldMarkPaymentFailed_whenStripeThrowsOnSavedCardConfirm() {
        SavedCard card = Instancio.create(SavedCard.class);
        ConfirmWithSavedCardRequest request = new ConfirmWithSavedCardRequest(card.getId(), "https://return.url");
        StripePaymentException stripeEx = mock(StripePaymentException.class);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(paymentService.findByStripePaymentIntentIdAndUser(STRIPE_PAYMENT_ID, currentUser))
                .thenReturn(payment);
        when(savedCardService.getCardByIdAndUser(card.getId(), currentUser)).thenReturn(card);
        when(stripeService.confirmPayment(any(), any(), any())).thenThrow(stripeEx);

        assertThatThrownBy(() ->
                paymentFacadeService.confirmPaymentWithSavedCard(STRIPE_PAYMENT_ID, request))
                .isEqualTo(stripeEx);

        verify(paymentService).updatePaymentStatus(STRIPE_PAYMENT_ID, PaymentStatus.FAILED);
        verify(paymentService, never()).updateSavedCard(any(), any());
    }

    // getPaymentStatus

    /**
     * Проверяет что статус обновляется в БД когда Stripe возвращает новый статус.
     */
    @Test
    void shouldUpdateStatus_whenStripeStatusDiffers() {
        payment = Instancio.of(Payment.class)
                .set(field(Payment::getUser), currentUser)
                .set(field(Payment::getStripePaymentIntentId), STRIPE_PAYMENT_ID)
                .set(field(Payment::getStatus), PaymentStatus.PENDING)
                .create();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(paymentService.findByStripePaymentIntentIdAndUser(STRIPE_PAYMENT_ID, currentUser))
                .thenReturn(payment);
        when(stripeService.getPaymentStatus(STRIPE_PAYMENT_ID)).thenReturn(stripeSuccessResponse);

        paymentFacadeService.getPaymentStatus(STRIPE_PAYMENT_ID);

        verify(paymentService).updatePaymentStatus(STRIPE_PAYMENT_ID, PaymentStatus.SUCCEEDED);
    }

}
