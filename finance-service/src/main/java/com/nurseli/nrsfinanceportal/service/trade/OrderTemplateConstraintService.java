package com.nurseli.nrsfinanceportal.service.trade;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OrderTemplateConstraintService {
    private static final Map<OrderTemplateType, Set<AssetType>> TEMPLATE_TO_ASSET_TYPES = new EnumMap<>(OrderTemplateType.class);
    private static final Pattern ISIN_PATTERN = Pattern.compile("^TR[A-Z0-9]{10}$");
    private static final Pattern FUTURES_CONTRACT_PATTERN = Pattern.compile("^[A-Z0-9_]+\\d{4}$");

    static {
        TEMPLATE_TO_ASSET_TYPES.put(OrderTemplateType.SPOT, Set.of(AssetType.CRYPTO, AssetType.FX, AssetType.STOCK, AssetType.METAL));
        TEMPLATE_TO_ASSET_TYPES.put(OrderTemplateType.FUTURES, Set.of(AssetType.FX, AssetType.STOCK, AssetType.METAL));
        TEMPLATE_TO_ASSET_TYPES.put(OrderTemplateType.FIXED_INCOME, Set.of(AssetType.FUND));
    }

    public OrderTemplateType inferTemplate(AssetType assetType) {
        if (assetType == null) return null;
        if (assetType == AssetType.FUND) return OrderTemplateType.FIXED_INCOME;
        return OrderTemplateType.SPOT;
    }

    public OrderTemplateType inferTemplate(AssetType assetType, String symbol) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase();
        if (ISIN_PATTERN.matcher(normalizedSymbol).matches()) {
            return OrderTemplateType.FIXED_INCOME;
        }
        if (FUTURES_CONTRACT_PATTERN.matcher(normalizedSymbol).matches()) {
            return OrderTemplateType.FUTURES;
        }
        return inferTemplate(assetType);
    }

    public boolean isAllowed(OrderTemplateType templateType, AssetType assetType, String symbol) {
        if (templateType == null || assetType == null) return false;
        OrderTemplateType inferred = inferTemplate(assetType, symbol);
        if (inferred != null && inferred != templateType) {
            return false;
        }
        return TEMPLATE_TO_ASSET_TYPES.getOrDefault(templateType, Set.of()).contains(assetType);
    }
}
