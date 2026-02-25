package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.ReviewTaskView;
import com.nurseli.nrsfinanceportal.common.dto.TaskActionRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTask;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskType;
import com.nurseli.nrsfinanceportal.service.ReviewTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
public class TaskController {

    private final ReviewTaskService reviewTaskService;

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
        return ResponseEntity.ok(ApiResponse.success(
                ReviewTaskView.from(reviewTaskService.getById(id))));
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