package com.nurseli.nrsfinanceportal.scheduler;

import com.nurseli.nrsfinanceportal.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEscalationScheduler {

    private final ReviewTaskService reviewTaskService;

    @Scheduled(cron = "0 */15 * * * *") // her 15 dakika
    public void escalateOverdueTasks() {
        reviewTaskService.escalateOverdueTasks();
    }
}