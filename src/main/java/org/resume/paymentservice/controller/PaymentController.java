package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.resume.paymentservice.model.dto.request.ConfirmPaymentRequest;
import org.resume.paymentservice.model.dto.request.ConfirmWithSavedCardRequest;
import org.resume.paymentservice.model.dto.request.CreatePaymentRequest;
import org.resume.paymentservice.model.dto.request.RefundRequest;
import org.resume.paymentservice.model.dto.response.PaymentResponse;
import org.resume.paymentservice.model.dto.response.RefundResponse;
import org.resume.paymentservice.service.facade.PaymentFacadeService;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/payments")
public class PaymentController {

    private final PaymentFacadeService paymentFacadeService;

    @Operation(summary = "Создание платежа",
            description = "Создаёт Stripe PaymentIntent. Возвращает `clientSecret` для подтверждения на стороне клиента.")
    @ApiResponse(responseCode = "502", description = "Ошибка на стороне Stripe")
    @PostMapping
    public CommonResponse<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest createPaymentRequest
    ) {

        PaymentResponse paymentResponse = paymentFacadeService.createPayment(createPaymentRequest);
        return CommonResponse.success(paymentResponse);
    }

    @Operation(summary = "Подтверждение платежа новой картой",
            description = "Подтверждает PaymentIntent через `paymentMethodId` полученный от Stripe.js на фронтенде.")
    @ApiResponse(responseCode = "404", description = "Платёж не найден")
    @ApiResponse(responseCode = "502", description = "Ошибка на стороне Stripe")
    @PostMapping("/{paymentIntentId}/confirm")
    public CommonResponse<PaymentResponse> confirmPayment(
            @Parameter(description = "Stripe PaymentIntent ID", example = "pi_3T4hhvtestyBFap60Hso8KXt")
            @PathVariable String paymentIntentId,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        PaymentResponse response = paymentFacadeService.confirmPayment(paymentIntentId, request);
        return CommonResponse.success(response);
    }

    @Operation(summary = "Подтверждение платежа сохранённой картой",
            description = "Подтверждает PaymentIntent используя ранее сохранённую карту клиента.")
    @ApiResponse(responseCode = "404", description = "Платёж или карта не найдены")
    @ApiResponse(responseCode = "502", description = "Ошибка на стороне Stripe")
    @PostMapping("/{paymentIntentId}/confirm/saved-card")
    public CommonResponse<PaymentResponse> confirmPaymentWithSavedCard(
            @Parameter(description = "Stripe PaymentIntent ID", example = "pi_3T4hhvtestyBFap60Hso8KXt")
            @PathVariable String paymentIntentId,
            @Valid @RequestBody ConfirmWithSavedCardRequest request
    ) {
        PaymentResponse response = paymentFacadeService.confirmPaymentWithSavedCard(paymentIntentId, request);
        return CommonResponse.success(response);
    }

    @Operation(summary = "Запрос на возврат",
            description = "Создаёт заявку на возврат средств. Переходит в статус `PENDING` — требует подтверждения сотрудником поддержки.")
    @ApiResponse(responseCode = "404", description = "Платёж не найден")
    @ApiResponse(responseCode = "409", description = "Возврат по этому платежу уже существует")
    @PostMapping("/{paymentIntentId}/refund")
    public CommonResponse<RefundResponse> requestRefund(
            @Parameter(description = "Stripe PaymentIntent ID", example = "pi_3T4hhvtestyBFap60Hso8KXt")
            @PathVariable String paymentIntentId,
            @Valid @RequestBody RefundRequest request
    ) {
        RefundResponse response = paymentFacadeService.createRefund(paymentIntentId, request);
        return CommonResponse.success(response);
    }

    @Operation(summary = "Получение статуса платежа",
            description = "Возвращает текущий статус платежа из Stripe. Клиент может просматривать только свои платежи.")
    @ApiResponse(responseCode = "403", description = "Платёж принадлежит другому пользователю")
    @ApiResponse(responseCode = "404", description = "Платёж не найден")
    @GetMapping("/{paymentId}")
    public CommonResponse<PaymentResponse> getPayment(
            @Parameter(description = "Stripe PaymentIntent ID", example = "pi_3OqXkLGswygInput00001")
            @PathVariable String paymentId
    ) {
        PaymentResponse paymentResponse = paymentFacadeService.getPaymentStatus(paymentId);
        return CommonResponse.success(paymentResponse);
    }

}
