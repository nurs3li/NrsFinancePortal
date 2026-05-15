package com.nurseli.marketdata.infrastructure.bist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BIST dış provider çağrı sonucu (HTTP + parse orchestration).
 */
public final class BistProviderResult<T> {

    private final boolean success;
    private final boolean partial;
    private final T data;
    private final BistProviderError error;
    private final List<String> warnings;

    private BistProviderResult(
            boolean success,
            boolean partial,
            T data,
            BistProviderError error,
            List<String> warnings) {
        this.success = success;
        this.partial = partial;
        this.data = data;
        this.error = error;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean success() {
        return success;
    }

    public boolean partial() {
        return partial;
    }

    public T data() {
        return data;
    }

    public BistProviderError error() {
        return error;
    }

    public List<String> warnings() {
        return warnings;
    }

    public static <T> BistProviderResult<T> success(T data) {
        return new BistProviderResult<>(true, false, data, null, List.of());
    }

    public static <T> BistProviderResult<T> partial(T data, List<String> warnings) {
        List<String> w = warnings == null ? List.of() : List.copyOf(warnings);
        return new BistProviderResult<>(true, true, data, null, w);
    }

    public static <T> BistProviderResult<T> failure(BistProviderError error) {
        return new BistProviderResult<>(false, false, null, error, List.of());
    }

    public static <T> BistProviderResult<T> disabled(String message) {
        BistProviderError err =
                new BistProviderError(
                        BistProviderSource.IS_YATIRIM,
                        "",
                        message,
                        null,
                        null,
                        java.time.Instant.now());
        return new BistProviderResult<>(false, false, null, err, List.of());
    }

    /**
     * Geçmiş çağrıları için: başarılı ama satır yok (boş liste). Uyarı metni opsiyonel.
     */
    @SuppressWarnings("unchecked")
    public static <T> BistProviderResult<T> empty(String message) {
        List<String> w = message == null || message.isBlank() ? List.of() : List.of(message);
        return (BistProviderResult<T>) new BistProviderResult<>(true, false, List.of(), null, w);
    }

    /** Parser uyarılarını kopyalanabilir liste olarak döndürür (birleştirme için). */
    public static List<String> copyWarnings(List<String> w) {
        if (w == null || w.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(w);
    }

    public static List<String> mergeWarnings(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }
}
