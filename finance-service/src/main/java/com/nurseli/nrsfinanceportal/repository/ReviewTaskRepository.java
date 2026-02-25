package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.task.ReviewTask;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {

    Page<ReviewTask> findByAssigneeRoleAndStatusInOrderByDueAtAsc(
            String assigneeRole,
            List<ReviewTaskStatus> statuses,
            Pageable pageable);

    Page<ReviewTask> findByAssigneeRoleOrderByCreatedAtDesc(
            String assigneeRole,
            Pageable pageable);

    @Query("SELECT t FROM ReviewTask t LEFT JOIN FETCH t.account WHERE t.id = :id")
    java.util.Optional<ReviewTask> findByIdWithAccount(@Param("id") Long id);

    List<ReviewTask> findByStatusAndDueAtBefore(ReviewTaskStatus status, Instant dueAt);

    Page<ReviewTask> findByAssigneeRoleAndTypeInOrderByCreatedAtDesc(
            String assigneeRole,
            List<ReviewTaskType> types,
            Pageable pageable);
}