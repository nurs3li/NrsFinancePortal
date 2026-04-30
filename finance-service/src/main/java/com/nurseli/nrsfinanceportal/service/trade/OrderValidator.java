package com.nurseli.nrsfinanceportal.service.trade;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderValidator {
    private final OrderTemplateConstraintService constraintService;

    @Value("${app.trade.order-form-v2-enabled:false}")
    private boolean orderFormV2Enabled;

    public TradeRequest normalizeAndValidate(TradeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("MARKET_ORDER_RESTRICTION: Emir isteği boş olamaz.");
        }
        if (request.assetType() == null || request.tradeType() == null || request.quantity() == null || request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("MARKET_ORDER_RESTRICTION: Zorunlu emir alanları eksik.");
        }
        OrderTemplateType resolvedTemplate = request.templateType() != null
                ? request.templateType()
                : constraintService.inferTemplate(request.assetType(), request.symbol());

        if (orderFormV2Enabled && !constraintService.isAllowed(resolvedTemplate, request.assetType(), request.symbol())) {
            throw new IllegalArgumentException(
                    "MARKET_ORDER_RESTRICTION: Seçilen şablon, varlık türü veya sembol birbiriyle uyumsuz. Şablon="
                            + resolvedTemplate + ", Varlık=" + request.assetType() + ", Sembol=" + request.symbol()
            );
        }

        return new TradeRequest(
                request.assetType(),
                request.symbol(),
                request.quantity(),
                request.tradeType(),
                resolvedTemplate,
                request.attributes()
        );
    }
}
