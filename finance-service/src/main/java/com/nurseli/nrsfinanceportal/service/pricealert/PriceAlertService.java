package com.nurseli.nrsfinanceportal.service.pricealert;

import com.nurseli.nrsfinanceportal.config.PriceAlertProperties;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricealert.*;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.dto.pricealert.PriceAlertCreateRequest;
import com.nurseli.nrsfinanceportal.dto.pricealert.PriceAlertDto;
import com.nurseli.nrsfinanceportal.dto.pricealert.PriceAlertUpdateRequest;
import com.nurseli.nrsfinanceportal.repository.PriceAlertRepository;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertRepository priceAlertRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PriceAlertProperties properties;

    @Transactional(readOnly = true)
    public List<PriceAlertDto> listForCurrentUser() {
        Long userId = currentUserResolver.getCurrentUserId();
        return priceAlertRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PriceAlertDto::from)
                .toList();
    }

    @Transactional
    public PriceAlertDto create(PriceAlertCreateRequest request) {
        validateRequest(request);
        User user = currentUserResolver.getOrCreateCurrentUser();
        ensureActiveLimit(user.getId());

        PriceAlert entity = new PriceAlert();
        entity.setUser(user);
        applyCreateFields(entity, request);
        return PriceAlertDto.from(priceAlertRepository.save(entity));
    }

    @Transactional
    public PriceAlertDto update(Long id, PriceAlertUpdateRequest request) {
        PriceAlert entity = requireOwnedAlert(id);
        if (request.conditionType() != null && !request.conditionType().isBlank()) {
            entity.setConditionType(parseCondition(request.conditionType()));
        }
        if (request.threshold() != null) {
            validateThreshold(request.threshold(), entity.getConditionType());
            entity.setThreshold(request.threshold());
        }
        if (request.changeWindow() != null) {
            entity.setChangeWindow(parseWindow(request.changeWindow()));
        }
        if (request.channels() != null && !request.channels().isBlank()) {
            entity.setChannels(parseChannels(request.channels()));
        }
        if (request.repeatAlert() != null) {
            entity.setRepeatAlert(request.repeatAlert());
        }
        if (request.cooldownHours() != null) {
            entity.setCooldownHours(Math.max(0, request.cooldownHours()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            entity.setStatus(parseStatus(request.status()));
        }
        return PriceAlertDto.from(priceAlertRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        PriceAlert entity = requireOwnedAlert(id);
        priceAlertRepository.delete(entity);
    }

    private PriceAlert requireOwnedAlert(Long id) {
        Long userId = currentUserResolver.getCurrentUserId();
        PriceAlert entity = priceAlertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm bulunamadı"));
        if (!entity.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu alarm size ait değil");
        }
        return entity;
    }

    private void ensureActiveLimit(Long userId) {
        long active = priceAlertRepository.countByUserIdAndStatus(userId, PriceAlertStatus.ACTIVE);
        if (active >= properties.getMaxActivePerUser()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "En fazla " + properties.getMaxActivePerUser() + " aktif alarm tanımlayabilirsiniz");
        }
    }

    private void applyCreateFields(PriceAlert entity, PriceAlertCreateRequest request) {
        AssetType assetType = parseAssetType(request.assetType());
        String symbol = normalizeSymbol(assetType, request.symbol());
        PriceAlertConditionType condition = parseCondition(request.conditionType());
        entity.setAssetType(assetType);
        entity.setSymbol(symbol);
        entity.setConditionType(condition);
        entity.setThreshold(request.threshold());
        entity.setChangeWindow(resolveWindow(request.changeWindow(), assetType, condition));
        entity.setChannels(parseChannels(request.channels()));
        entity.setStatus(PriceAlertStatus.ACTIVE);
        entity.setRepeatAlert(request.repeatAlert() == null || request.repeatAlert());
        entity.setCooldownHours(request.cooldownHours() != null ? Math.max(0, request.cooldownHours()) : 24);
    }

    private void validateRequest(PriceAlertCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İstek gövdesi boş");
        }
        if (request.assetType() == null || request.assetType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assetType zorunlu");
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol zorunlu");
        }
        if (request.conditionType() == null || request.conditionType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conditionType zorunlu");
        }
        PriceAlertConditionType condition = parseCondition(request.conditionType());
        validateThreshold(request.threshold(), condition);
    }

    private static void validateThreshold(BigDecimal threshold, PriceAlertConditionType condition) {
        if (threshold == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "threshold zorunlu");
        }
        boolean pct = condition == PriceAlertConditionType.CHANGE_PCT_GTE
                || condition == PriceAlertConditionType.CHANGE_PCT_LTE;
        if (!pct && threshold.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiyat eşiği sıfırdan büyük olmalı");
        }
    }

    private static AssetType parseAssetType(String raw) {
        try {
            return AssetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz assetType: " + raw);
        }
    }

    private static PriceAlertConditionType parseCondition(String raw) {
        try {
            return PriceAlertConditionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz conditionType: " + raw);
        }
    }

    private static PriceAlertChangeWindow parseWindow(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PriceAlertChangeWindow.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz changeWindow: " + raw);
        }
    }

    private static PriceAlertStatus parseStatus(String raw) {
        try {
            return PriceAlertStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz status: " + raw);
        }
    }

    private static PriceAlertChannels parseChannels(String raw) {
        if (raw == null || raw.isBlank()) {
            return PriceAlertChannels.BOTH;
        }
        try {
            return PriceAlertChannels.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PriceAlertChannels.BOTH;
        }
    }

    private static PriceAlertChangeWindow resolveWindow(
            String requested,
            AssetType assetType,
            PriceAlertConditionType condition) {
        boolean pct = condition == PriceAlertConditionType.CHANGE_PCT_GTE
                || condition == PriceAlertConditionType.CHANGE_PCT_LTE;
        if (!pct) {
            return null;
        }
        PriceAlertChangeWindow parsed = parseWindow(requested);
        if (parsed != null) {
            return parsed;
        }
        return PriceAlertMarketSnapshotService.defaultWindowFor(assetType, true);
    }

    static String normalizeSymbol(AssetType type, String symbol) {
        if (symbol == null) {
            return "";
        }
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        if (type == AssetType.FX) {
            if ("USD".equals(s) || "EUR".equals(s) || "GBP".equals(s)) {
                return s + "TRY";
            }
            if (!s.endsWith("TRY") && s.length() == 3) {
                return s + "TRY";
            }
        }
        if (type == AssetType.METAL && ("ALTIN_TRY".equals(s) || "GRAM_ALTIN".equals(s) || "GOLD".equals(s))) {
            return "XAU_TRY";
        }
        return SymbolNormalizer.normalize(type, s);
    }
}
