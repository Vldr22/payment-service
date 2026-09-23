package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.resume.paymentservice.model.dto.request.AddCardRequest;
import org.resume.paymentservice.model.dto.response.SavedCardResponse;
import org.resume.paymentservice.service.facade.CardFacadeService;
import org.resume.paymentservice.utils.SuccessMessages;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Cards")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/cards")
public class CardController {

    private final CardFacadeService cardFacadeService;

    @Operation(summary = "Добавление карты",
            description = "Привязывает Stripe PaymentMethod к аккаунту клиента. Сырые данные карты на сервер не передаются — только токен от Stripe.js.")
    @ApiResponse(responseCode = "409", description = "Карта уже привязана к аккаунту")
    @ApiResponse(responseCode = "502", description = "Ошибка на стороне Stripe")
    @PostMapping
    public CommonResponse<SavedCardResponse> addCard(
            @Valid @RequestBody AddCardRequest request
    ) {
        return CommonResponse.success(cardFacadeService.addCard(request));
    }

    @Operation(summary = "Список сохранённых карт",
            description = "Возвращает все привязанные карты текущего клиента.")
    @GetMapping
    public CommonResponse<List<SavedCardResponse>> getUserCards() {
        return CommonResponse.success(cardFacadeService.getUserCards());
    }

    @Operation(summary = "Удаление карты",
            description = "Отвязывает карту от аккаунта и удаляет PaymentMethod из Stripe. Нельзя удалить карту привязанную к активной подписке.")
    @ApiResponse(responseCode = "404", description = "Карта не найдена")
    @ApiResponse(responseCode = "409", description = "Карта привязана к активной подписке")
    @ApiResponse(responseCode = "502", description = "Ошибка на стороне Stripe")
    @DeleteMapping("/{cardId}")
    public CommonResponse<String> removeCard(
            @Parameter(description = "ID сохранённой карты", example = "42")
            @PathVariable Long cardId
    ) {
        cardFacadeService.removeCard(cardId);
        return CommonResponse.success(SuccessMessages.CARD_REMOVED_SUCCESS);
    }

    @Operation(summary = "Установка карты по умолчанию",
            description = "Помечает карту как основную для автоматических списаний по подписке.")
    @ApiResponse(responseCode = "404", description = "Карта не найдена")
    @PatchMapping("/{cardId}/default")
    public CommonResponse<SavedCardResponse> setDefaultCard(
            @Parameter(description = "ID сохранённой карты", example = "42")
            @PathVariable Long cardId
    ) {
        return CommonResponse.success(cardFacadeService.setDefaultCard(cardId));
    }
}
