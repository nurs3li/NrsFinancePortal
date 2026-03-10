package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.ReviewTaskView;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus;
import com.nurseli.nrsfinanceportal.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTaskController {

    private final ReviewTaskService reviewTaskService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReviewTaskView>>> getAdminTasks(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        ReviewTaskStatus statusFilter = status != null ? ReviewTaskStatus.valueOf(status) : null;
        Page<ReviewTaskView> page = reviewTaskService.getAdminTasks(pageable, statusFilter);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewTaskView>> patchAdminTask(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String action = body.get("action");
        String reason = body.get("reason");
        ReviewTaskView view = reviewTaskService.executeAdminAction(id, action, reason);
        return ResponseEntity.ok(ApiResponse.success(view));
    }
    @PostMapping("/backfill-task-accounts")
    public ResponseEntity<ApiResponse<Integer>> backfillTaskAccounts() {
        int n = reviewTaskService.backfillAccountForExistingTasks();
        return ResponseEntity.ok(ApiResponse.success(n));
    }

    @PostMapping("/cleanup-duplicate-tasks")
    public ResponseEntity<ApiResponse<Integer>> cleanupDuplicateTasks() {
        int n = reviewTaskService.cleanupDuplicateTasks();
        return ResponseEntity.ok(ApiResponse.success(n));
    }
}