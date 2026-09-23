package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.resume.paymentservice.model.dto.request.CreateSubscriptionRequest;
import org.resume.paymentservice.model.dto.response.BillingAttemptResponse;
import org.resume.paymentservice.model.dto.response.SubscriptionResponse;
import org.resume.paymentservice.service.facade.SubscriptionFacadeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Subscriptions")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/subscriptions")
public class SubscriptionController {

    private final SubscriptionFacadeService subscriptionFacadeService;

    @Operation(summary = "Создание подписки",
            description = "Создаёт подписку для текущего клиента. Первое списание происходит автоматически через billing scheduler.")
    @ApiResponse(responseCode = "201", description = "Подписка создана")
    @ApiResponse(responseCode = "404", description = "Карта не найдена")
    @ApiResponse(responseCode = "409", description = "Активная подписка уже существует")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<SubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        SubscriptionResponse response = subscriptionFacadeService.createSubscription(request);
        return CommonResponse.success(response);
    }

    @Operation(summary = "Получение подписки",
            description = "Возвращает текущую активную подписку клиента.")
    @ApiResponse(responseCode = "404", description = "Активная подписка не найдена")
    @GetMapping
    public CommonResponse<SubscriptionResponse> getSubscription() {
        SubscriptionResponse response = subscriptionFacadeService.getSubscription();
        return CommonResponse.success(response);
    }

    @Operation(summary = "Отмена подписки",
            description = "Отменяет текущую подписку клиента.")
    @ApiResponse(responseCode = "404", description = "Активная подписка не найдена")
    @PatchMapping("/cancel")
    public CommonResponse<SubscriptionResponse> cancelSubscription() {
        SubscriptionResponse response = subscriptionFacadeService.cancelSubscription();
        return CommonResponse.success(response);
    }

    @Operation(summary = "История списаний",
            description = "Возвращает все попытки списания по подписке — успешные и неуспешные.")
    @ApiResponse(responseCode = "404", description = "Активная подписка не найдена")
    @GetMapping("/billing-history")
    public CommonResponse<List<BillingAttemptResponse>> getBillingHistory() {
        List<BillingAttemptResponse> response = subscriptionFacadeService.getBillingHistory();
        return CommonResponse.success(response);
    }
}
