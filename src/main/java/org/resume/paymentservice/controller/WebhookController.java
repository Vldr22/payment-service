package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.service.webhook.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Webhook")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/webhooks")
public class WebhookController {

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final WebhookService webhookService;

    @Operation(summary = "Обработка Stripe webhook",
            description = """
                    Принимает события от Stripe и обрабатывает их через цепочку handlers.
                    Подпись запроса верифицируется через заголовок `Stripe-Signature`.
                    
                    Обрабатываемые события:
                    * `payment_intent.succeeded` — подтверждает успешный платёж или списание по подписке
                    * `payment_intent.payment_failed` — фиксирует неудачную попытку
                    * `payment_intent.processing` — обновляет статус платежа на `PROCESSING`
                    * `payment_intent.canceled` — обновляет статус платежа на `CANCELED`
                    * `charge.refunded` — подтверждает успешный возврат средств
                    * `refund.failed` — фиксирует неудачный возврат
                    
                    Неизвестные типы событий игнорируются.
                    Формат payload: [Stripe Events API](https://docs.stripe.com/api/events)
                    """)
    @ApiResponse(responseCode = "200", description = "Событие успешно обработано")
    @ApiResponse(responseCode = "400", description = "Невалидная подпись или дублирующееся событие")
    @SecurityRequirements
    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(
            @RequestBody String payload,
            @Parameter(description = "Stripe webhook signature", required = true)
            @RequestHeader(STRIPE_SIGNATURE_HEADER) String signatureHeader
    ) {

        webhookService.createWebhookEvent(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }

}
