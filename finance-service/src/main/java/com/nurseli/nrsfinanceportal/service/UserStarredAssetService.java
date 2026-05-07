package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.domain.user.UserStarredAsset;
import com.nurseli.nrsfinanceportal.dto.*;
import com.nurseli.nrsfinanceportal.repository.UserStarredAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserStarredAssetService {

    private static final int MAX_ITEMS = 12;
    private static final int MAX_MARKET_TYPE_LEN = 24;
    private static final int MAX_SYMBOL_LEN = 32;
    private static final Set<String> ALLOWED_MARKET_TYPES = Set.of("FX", "METALS", "CRYPTO", "FUNDS", "EQUITY");
    private static final List<StarKey> DEFAULTS = List.of(
            new StarKey("METALS", "XAU_TRY"),
            new StarKey("FX", "USDTRY"),
            new StarKey("CRYPTO", "BTCUSDT"),
            new StarKey("FUNDS", "VWO"),
            new StarKey("EQUITY", "AAPL"),
            new StarKey("EQUITY", "GOOGL"),
            new StarKey("EQUITY", "TSLA"),
            new StarKey("CRYPTO", "ETHUSDT"),
            new StarKey("FX", "EURTRY"),
            new StarKey("FUNDS", "QQQ"),
            new StarKey("FUNDS", "SPY"),
            new StarKey("EQUITY", "MSFT")
    );

    private final CurrentUserResolver currentUserResolver;
    private final UserStarredAssetRepository userStarredAssetRepository;

    @Transactional(readOnly = true)
    public StarredAssetsResponse getCurrentUserStarredAssets() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        return buildResponse(user);
    }

    @Transactional
    public StarredAssetsResponse updateCurrentUserStarredAssets(StarredAssetsUpdateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        List<StarKey> normalized = normalizeAndValidate(request != null ? request.selected() : List.of());
        userStarredAssetRepository.deleteByUserId(user.getId());
        // Ensure old rows are physically removed before inserting the new ordered list.
        userStarredAssetRepository.flush();

        int position = 1;
        for (StarKey key : normalized) {
            userStarredAssetRepository.save(UserStarredAsset.of(user, key.marketType(), key.symbol(), position++));
        }
        return buildResponse(user);
    }

    private StarredAssetsResponse buildResponse(User user) {
        List<UserStarredAsset> selectedRows = userStarredAssetRepository.findByUserIdOrderByPositionAsc(user.getId());

        List<StarredAssetSelectionDto> selected = selectedRows.stream()
                .map(r -> new StarredAssetSelectionDto(r.getMarketType(), r.getSymbol(), r.getPosition()))
                .toList();

        LinkedHashSet<StarKey> resolvedSet = new LinkedHashSet<>();
        List<StarredAssetResolvedDto> resolved = new ArrayList<>();

        for (StarredAssetSelectionDto s : selected) {
            if (resolvedSet.size() >= MAX_ITEMS) break;
            StarKey key = new StarKey(s.marketType(), s.symbol());
            if (resolvedSet.add(key)) {
                resolved.add(new StarredAssetResolvedDto(key.marketType(), key.symbol(), resolved.size() + 1, false));
            }
        }
        for (StarKey def : DEFAULTS) {
            if (resolvedSet.size() >= MAX_ITEMS) break;
            if (resolvedSet.add(def)) {
                resolved.add(new StarredAssetResolvedDto(def.marketType(), def.symbol(), resolved.size() + 1, true));
            }
        }

        return new StarredAssetsResponse(MAX_ITEMS, selected, resolved);
    }

    private List<StarKey> normalizeAndValidate(List<StarredAssetSelectionRequest> input) {
        List<StarredAssetSelectionRequest> list = input != null ? input : List.of();
        if (list.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("En fazla " + MAX_ITEMS + " varlik secilebilir.");
        }

        LinkedHashSet<StarKey> uniq = new LinkedHashSet<>();
        for (StarredAssetSelectionRequest row : list) {
            String marketType = row != null && row.marketType() != null ? normalizeMarketType(row.marketType()) : "";
            String symbol = row != null && row.symbol() != null ? row.symbol().trim().toUpperCase() : "";
            if (marketType.isBlank() || symbol.isBlank()) {
                throw new IllegalArgumentException("marketType ve symbol zorunludur.");
            }
            if (!ALLOWED_MARKET_TYPES.contains(marketType)) {
                throw new IllegalArgumentException("Desteklenmeyen marketType: " + marketType);
            }
            if (marketType.length() > MAX_MARKET_TYPE_LEN) {
                throw new IllegalArgumentException("marketType uzunluğu gecersiz: " + marketType);
            }
            if (symbol.length() > MAX_SYMBOL_LEN) {
                throw new IllegalArgumentException("symbol uzunluğu gecersiz: " + symbol);
            }
            uniq.add(new StarKey(marketType, symbol));
            if (uniq.size() > MAX_ITEMS) {
                throw new IllegalArgumentException("En fazla " + MAX_ITEMS + " varlik secilebilir.");
            }
        }
        return new ArrayList<>(uniq);
    }

    private String normalizeMarketType(String raw) {
        String marketType = raw == null ? "" : raw.trim().toUpperCase();
        return switch (marketType) {
            case "METAL" -> "METALS";
            case "FUND" -> "FUNDS";
            default -> marketType;
        };
    }

    private record StarKey(String marketType, String symbol) {}
}
