package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.NextCursor;
import com.nurseli.nrsfinanceportal.common.dto.TimelinePageResponse;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedTimelineDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TimelineReadModelService {

    private static final long CACHE_TTL_SECONDS = 60;

    private final RedisTemplate<String, Object> redisTemplate;
    private final UnifiedTimelineQueryService timelineQueryService;

    public TimelinePageResponse getTimeline(
            Long userId,
            Instant cursorAt,
            Long cursorId,
            int size
    ) {

        String cacheKey = buildCacheKey(userId, cursorAt, cursorId, size);

        // 1️⃣ Cache HIT
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof TimelinePageResponse response) {
            return response;
        }

        // 2️⃣ DB → size + 1 geldi
        List<UnifiedTimelineDto> fetched =
                timelineQueryService.getTimelinePage(
                        userId,
                        cursorAt,
                        cursorId,
                        size
                );

        boolean hasMore = fetched.size() > size;

        // sadece size kadarını dön
        List<UnifiedTimelineDto> items =
                hasMore ? fetched.subList(0, size) : fetched;

        NextCursor nextCursor = null;

        if (hasMore) {
            UnifiedTimelineDto last = items.get(items.size() - 1);
            nextCursor = new NextCursor(
                    last.occurredAt(),
                    last.tradeId()
            );
        }

        TimelinePageResponse response =
                new TimelinePageResponse(items, nextCursor, hasMore);

        // 3️⃣ Cache yaz
        redisTemplate.opsForValue()
                .set(cacheKey, response, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        return response;
    }

    private String buildCacheKey(
            Long userId,
            Instant cursorAt,
            Long cursorId,
            int size
    ) {
        return "timeline:%d:%s:%s:%d".formatted(
                userId,
                cursorAt != null ? cursorAt : "FIRST",
                cursorId != null ? cursorId : "NONE",
                size
        );
    }
}
