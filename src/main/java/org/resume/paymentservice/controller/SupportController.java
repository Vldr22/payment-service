package org.resume.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.resume.paymentservice.contants.ApiPaths;
import org.resume.paymentservice.model.dto.CommonResponse;
import org.resume.paymentservice.model.dto.response.RefundDetailResponse;
import org.resume.paymentservice.service.facade.SupportFacadeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Support")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/support")
public class SupportController {

    private final SupportFacadeService supportFacadeService;

    @Operation(summary = "Список заявок на возврат",
            description = "Возвращает все заявки на возврат со статусом `PENDING`. Требует роль `ROLE_EMPLOYEE`.")
    @GetMapping("/refunds/pending")
    public CommonResponse<List<RefundDetailResponse>> getPendingRefunds() {
        List<RefundDetailResponse> refunds = supportFacadeService.getPendingRefunds();
        return CommonResponse.success(refunds);
    }

    @Operation(summary = "Одобрить возврат",
            description = "Одобряет заявку и инициирует фактический возврат средств через Stripe. Требует роль `ROLE_EMPLOYEE`.")
    @ApiResponse(responseCode = "400", description = "Запрос уже обработан")
    @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    @ApiResponse(responseCode = "502", description = "Ошибка на стороне Stripe")
    @PostMapping("/refunds/{refundId}/approve")
    public CommonResponse<RefundDetailResponse> approveRefund(
            @Parameter(description = "ID заявки на возврат", example = "7")
            @PathVariable Long refundId
    ) {
        RefundDetailResponse response = supportFacadeService.approveRefund(refundId);
        return CommonResponse.success(response);
    }

    @Operation(summary = "Отклонить возврат",
            description = "Отклоняет заявку на возврат. Средства клиенту не возвращаются. Требует роль `ROLE_EMPLOYEE`.")
    @ApiResponse(responseCode = "400", description = "Запрос уже обработан")
    @ApiResponse(responseCode = "404", description = "Заявка не найдена")
    @PostMapping("/refunds/{refundId}/reject")
    public CommonResponse<RefundDetailResponse> rejectRefund(
            @Parameter(description = "ID заявки на возврат", example = "7")
            @PathVariable Long refundId
    ) {
        RefundDetailResponse response = supportFacadeService.rejectRefund(refundId);
        return CommonResponse.success(response);
    }

}
