package com.nurseli.nrsfinanceportal.api.dto;

import java.time.Instant;

/**
 * Cursor tabanlı sayfalama imleci; bir sonraki sayfa için zaman ve kayıt id'sini taşır.
 */
public record NextCursor(
        Instant cursorAt,
        Long cursorId
) {
}
