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
import java.util.Collection;
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

    @Query("SELECT t FROM ReviewTask t LEFT JOIN FETCH t.account a LEFT JOIN FETCH a.user WHERE t.id = :id")
    java.util.Optional<ReviewTask> findByIdWithAccountAndUser(@Param("id") Long id);

    List<ReviewTask> findByStatusAndDueAtBefore(ReviewTaskStatus status, Instant dueAt);
    List<ReviewTask> findByAccount_IdAndStatus(Long accountId, ReviewTaskStatus status);
    Page<ReviewTask> findByAssigneeRoleAndTypeInOrderByCreatedAtDesc(
            String assigneeRole,
            List<ReviewTaskType> types,
            Pageable pageable);

    boolean existsByStatusInAndAccount_Id(
            Collection<ReviewTaskStatus> statuses,
            Long accountId);

    List<ReviewTask> findByTypeAndStatusIn(
            ReviewTaskType type,
            Collection<ReviewTaskStatus> statuses);
}