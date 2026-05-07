package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.ViopBackfillProperties;
import com.nurseli.marketdata.domain.derivatives.DerivativeContract;
import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import com.nurseli.marketdata.domain.derivatives.OpenInterestSnapshot;
import com.nurseli.marketdata.repository.DerivativeContractRepository;
import com.nurseli.marketdata.repository.DerivativeSnapshotRepository;
import com.nurseli.marketdata.repository.OpenInterestSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ViopBackfillRunner implements ApplicationRunner {
    private static final Pattern FILE_DATE = Pattern.compile("viop_(\\d{8})\\.csv", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_FMT_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_FMT_TR = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ViopBackfillProperties props;
    private final DerivativeContractRepository contractRepository;
    private final DerivativeSnapshotRepository snapshotRepository;
    private final OpenInterestSnapshotRepository openInterestSnapshotRepository;
    private final ConfigurableApplicationContext context;
    private final ViopContractParser viopContractParser;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled()) {
            return;
        }
        BackfillStats stats = new BackfillStats();
        try {
            List<Path> files = listFiles();
            stats.totalFiles = files.size();
            log.info("[VIOP_BACKFILL] Starting file backfill. files={}, dir={}, pattern={}", files.size(), props.getDir(), props.getPattern());
            for (Path file : files) {
                try {
                    processFile(file, stats);
                } catch (Exception ex) {
                    stats.fileErrors++;
                    log.warn("[VIOP_BACKFILL] file failed: {} -> {}", file, ex.getMessage());
                }
            }
            log.info("[VIOP_BACKFILL] DONE files={}, rows={}, inserts={}, duplicates={}, rowErrors={}, fileErrors={}",
                    stats.totalFiles, stats.totalRows, stats.inserts, stats.duplicates, stats.rowErrors, stats.fileErrors);
        } catch (Exception ex) {
            log.error("[VIOP_BACKFILL] fatal error: {}", ex.getMessage(), ex);
        } finally {
            if (props.isShutdownOnComplete()) {
                log.info("[VIOP_BACKFILL] shutdownOnComplete=true, shutting down application");
                System.exit(org.springframework.boot.SpringApplication.exit(context, () -> 0));
            }
        }
    }

    private List<Path> listFiles() throws IOException {
        Path dir = Path.of(props.getDir());
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("viop_"))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted(Comparator.comparing(this::extractDateFromFileName))
                    .toList();
            return files;
        }
    }

    @Transactional
    protected void processFile(Path file, BackfillStats stats) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.size() < 2) {
            return;
        }
        String delimiter = lines.get(0).contains(";") ? ";" : ",";
        String[] rawHeaders = lines.get(0).split(delimiter, -1);
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < rawHeaders.length; i++) {
            headerIndex.put(normalize(rawHeaders[i]), i);
        }
        LocalDate fallbackDate = extractDateFromFileName(file);

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            stats.totalRows++;
            try {
                String[] values = line.split(delimiter, -1);
                String contractCode = get(values, headerIndex, "SOZLESME_KODU", "INSTRUMENT_SERIES");
                if (isHeaderRow(contractCode)) {
                    continue;
                }
                if (contractCode == null || contractCode.isBlank()) {
                    stats.rowErrors++;
                    continue;
                }
                String underlying = get(values, headerIndex, "DAYANAK_VARLIK", "UNDERLYING");
                String expiry = get(values, headerIndex, "VADE_TARIHI", "EXPIRATION_DATE");
                String type = get(values, headerIndex, "SOZLESME_TIPI", "INSTRUMENT_TYPE");
                BigDecimal settlement = parseDecimal(get(values, headerIndex, "UZLASMA_FIYATI", "SETTLEMENT_PRICE"));
                Long openInterest = parseLong(get(values, headerIndex, "ACIK_POZISYON", "OPEN_POSITION"));
                Long dailyVolume = parseLong(get(values, headerIndex, "ISLEM_MIKTARI", "TRADE_VOLUME"));
                String tradeDateText = get(values, headerIndex, "TARIH", "TRADE_DATE");
                LocalDateTime asOf = parseAsOf(tradeDateText, fallbackDate);

                String normalizedContract = viopContractParser.normalizeContractCode(contractCode);
                if (snapshotRepository.existsByContractCodeAndAsOf(normalizedContract, asOf)
                        && openInterestSnapshotRepository.existsByContractCodeAndAsOf(normalizedContract, asOf)) {
                    stats.duplicates++;
                    continue;
                }
                ensureContract(normalizedContract, underlying, expiry, type);

                if (!snapshotRepository.existsByContractCodeAndAsOf(normalizedContract, asOf)) {
                    DerivativeSnapshot snapshot = new DerivativeSnapshot();
                    snapshot.setContractCode(normalizedContract);
                    snapshot.setPrice(settlement == null ? BigDecimal.ZERO : settlement);
                    snapshot.setTheoreticalSpot(settlement == null ? BigDecimal.ZERO : settlement);
                    snapshot.setSource("VIOP_BULLETIN");
                    snapshot.setAsOf(asOf);
                    snapshot.setDataQuality("EXACT");
                    snapshot.setPriceSource("BULLETIN");
                    snapshot.setBasis(BigDecimal.ZERO);
                    snapshot.setMaintenanceMargin(settlement == null ? BigDecimal.ZERO : settlement.multiply(new BigDecimal("0.12")));
                    snapshot.setDaysToExpiry(viopContractParser.calculateDaysToExpiry(
                            viopContractParser.inferExpiryFromContractCode(normalizedContract, expiry)
                    ));
                    snapshotRepository.save(snapshot);
                    stats.inserts++;
                }

                if (!openInterestSnapshotRepository.existsByContractCodeAndAsOf(normalizedContract, asOf)) {
                    OpenInterestSnapshot oi = new OpenInterestSnapshot();
                    oi.setContractCode(normalizedContract);
                    oi.setOpenInterest(openInterest == null ? 0L : openInterest);
                    oi.setDailyVolume(dailyVolume);
                    oi.setAsOf(asOf);
                    openInterestSnapshotRepository.save(oi);
                    stats.inserts++;
                }
            } catch (Exception ex) {
                stats.rowErrors++;
                log.warn("[VIOP_BACKFILL] row parse failed in {} line {}: {}", file.getFileName(), i + 1, ex.getMessage());
            }
        }
    }

    private void ensureContract(String contractCode, String underlying, String expiry, String type) {
        contractRepository.findByContractCode(contractCode).orElseGet(() -> {
            DerivativeContract c = new DerivativeContract();
            c.setContractCode(contractCode);
            c.setUnderlying((underlying == null || underlying.isBlank()) ? inferUnderlying(contractCode) : underlying);
            String normalizedExpiry = viopContractParser.inferExpiryFromContractCode(contractCode, expiry);
            c.setExpiry(normalizedExpiry == null ? "2099-12-31" : normalizedExpiry);
            c.setType((type == null || type.isBlank()) ? "FUTURES" : type);
            return contractRepository.save(c);
        });
    }

    private String inferUnderlying(String contractCode) {
        if (contractCode == null || contractCode.isBlank()) return "UNKNOWN";
        String t = contractCode.toUpperCase(Locale.ROOT);
        if (t.startsWith("F_")) {
            return t.substring(2).replaceAll("\\d", "");
        }
        return t.replaceAll("\\d", "");
    }

    private String normalize(String header) {
        return header == null ? "" : header.trim()
                .toUpperCase(Locale.ROOT)
                .replace('İ', 'I')
                .replace('Ş', 'S')
                .replace('Ğ', 'G')
                .replace('Ü', 'U')
                .replace('Ö', 'O')
                .replace('Ç', 'C')
                .replace(" ", "_")
                .replace("-", "_");
    }

    private String get(String[] values, Map<String, Integer> index, String... keys) {
        for (String key : keys) {
            Integer idx = index.get(key);
            if (idx != null && idx >= 0 && idx < values.length) {
                String v = values[idx] == null ? null : values[idx].trim();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private boolean isHeaderRow(String contractCode) {
        if (contractCode == null) return false;
        String normalized = normalize(contractCode);
        return "INSTRUMENT_SERIES".equals(normalized) || "SOZLESME_KODU".equals(normalized);
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String text = raw.trim();
            if (text.contains(",") && text.contains(".")) {
                return new BigDecimal(text.replace(".", "").replace(",", "."));
            }
            if (text.contains(",")) {
                return new BigDecimal(text.replace(",", "."));
            }
            return new BigDecimal(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String v = raw.replace(".", "").replace(",", "").trim();
            return Long.parseLong(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate extractDateFromFileName(Path file) {
        String name = file.getFileName().toString();
        Matcher m = FILE_DATE.matcher(name);
        if (!m.find()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(m.group(1), FILE_DATE_FMT);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private LocalDateTime parseAsOf(String rawDate, LocalDate fallbackDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return fallbackDate.atStartOfDay();
        }
        try {
            return LocalDate.parse(rawDate, DATE_FMT_ISO).atStartOfDay();
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(rawDate, DATE_FMT_TR).atStartOfDay();
            } catch (Exception ignoredAgain) {
                return fallbackDate.atStartOfDay();
            }
        }
    }

    private static class BackfillStats {
        int totalFiles;
        int totalRows;
        int inserts;
        int duplicates;
        int rowErrors;
        int fileErrors;
    }
}

