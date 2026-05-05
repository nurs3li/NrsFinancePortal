package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.FmTaskSummaryDto;
import com.nurseli.nrsfinanceportal.common.dto.ReviewTaskView;
import com.nurseli.nrsfinanceportal.common.dto.TaskActionRequest;
import com.nurseli.nrsfinanceportal.common.dto.TaskInvestigationContext;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskType;
import com.nurseli.nrsfinanceportal.integration.sse.TaskPoolSseService;
import com.nurseli.nrsfinanceportal.service.ReviewTaskService;
import com.nurseli.nrsfinanceportal.service.TaskInvestigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
public class TaskController {

    private final ReviewTaskService reviewTaskService;
    private final TaskInvestigationService taskInvestigationService;
    private final TaskPoolSseService taskPoolSseService;

    @GetMapping(value = "/sse/fm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public SseEmitter streamFmTaskPool() {
        return taskPoolSseService.subscribeFm();
    }

    @GetMapping(value = "/sse/admin", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public SseEmitter streamAdminTaskAlerts() {
        return taskPoolSseService.subscribeAdmin();
    }

    @GetMapping("/pool")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<ApiResponse<Page<ReviewTaskView>>> getFmPool(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewTaskService.getFmTaskPool(pageable)));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<ApiResponse<ReviewTaskView>> claim(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(reviewTaskService.claimTask(id)));
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<ApiResponse<FmTaskSummaryDto>> fmSummary() {
        return ResponseEntity.ok(ApiResponse.success(reviewTaskService.getFmTaskSummary()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<ReviewTaskView>>> getMyTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable) {
        ReviewTaskStatus statusFilter = status != null ? ReviewTaskStatus.valueOf(status) : null;
        ReviewTaskType typeFilter = type != null ? ReviewTaskType.valueOf(type) : null;
        Page<ReviewTaskView> page = reviewTaskService.getMyTasks(pageable, statusFilter, typeFilter);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewTaskView>> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(reviewTaskService.getTaskViewForCurrentUser(id)));
    }

    @GetMapping("/{id}/context")
    public ResponseEntity<ApiResponse<TaskInvestigationContext>> getTaskContext(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(taskInvestigationService.getContext(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewTaskView>> patchTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskActionRequest request) {
        ReviewTaskView view = reviewTaskService.executeAction(
                id, request.action(), request.accountId());
        return ResponseEntity.ok(ApiResponse.success(view));
    }
}