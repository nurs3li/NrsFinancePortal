package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.task.ReviewTask;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {

    @EntityGraph(attributePaths = {"account", "account.user"})
    Page<ReviewTask> findByAssigneeRoleAndStatusInOrderByDueAtAsc(
            String assigneeRole,
            List<ReviewTaskStatus> statuses,
            Pageable pageable);

    @EntityGraph(attributePaths = {"account", "account.user"})
    Page<ReviewTask> findByAssigneeRoleOrderByCreatedAtDesc(
            String assigneeRole,
            Pageable pageable);

    @Query("SELECT t FROM ReviewTask t LEFT JOIN FETCH t.account WHERE t.id = :id")
    java.util.Optional<ReviewTask> findByIdWithAccount(@Param("id") Long id);

    @Query("SELECT t FROM ReviewTask t LEFT JOIN FETCH t.account a LEFT JOIN FETCH a.user WHERE t.id = :id")
    java.util.Optional<ReviewTask> findByIdWithAccountAndUser(@Param("id") Long id);

    List<ReviewTask> findByStatusAndDueAtBefore(ReviewTaskStatus status, Instant dueAt);
    List<ReviewTask> findByAccount_IdAndStatus(Long accountId, ReviewTaskStatus status);
    @EntityGraph(attributePaths = {"account", "account.user"})
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

    @EntityGraph(attributePaths = {"account", "account.user"})
    @Query("""
            SELECT t FROM ReviewTask t
            WHERE t.assigneeRole = 'FINANCE_MANAGER'
              AND t.status = com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus.PENDING
              AND t.assignedFmKeycloakId IS NULL
            ORDER BY t.createdAt ASC
            """)
    Page<ReviewTask> findFmPoolUnclaimed(Pageable pageable);

    @EntityGraph(attributePaths = {"account", "account.user"})
    Page<ReviewTask> findByAssigneeRoleAndAssignedFmKeycloakIdAndStatusInOrderByClaimedAtDesc(
            String assigneeRole,
            String assignedFmKeycloakId,
            List<ReviewTaskStatus> statuses,
            Pageable pageable);

    long countByAssigneeRoleAndStatusAndAssignedFmKeycloakIdIsNull(
            String assigneeRole,
            ReviewTaskStatus status);

    long countByAssigneeRoleAndAssignedFmKeycloakIdAndStatus(
            String assigneeRole,
            String assignedFmKeycloakId,
            ReviewTaskStatus status);

    long countByAssigneeRoleAndAssignedFmKeycloakIdAndStatusIn(
            String assigneeRole,
            String assignedFmKeycloakId,
            Collection<ReviewTaskStatus> statuses);

    List<ReviewTask> findByAssigneeRoleAndStatusInAndAssignedFmKeycloakIdIsNullAndCreatedAtBefore(
            String assigneeRole,
            Collection<ReviewTaskStatus> statuses,
            Instant createdBefore);

    List<ReviewTask> findByAssigneeRoleAndStatusAndClaimedAtBefore(
            String assigneeRole,
            ReviewTaskStatus status,
            Instant claimedBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ReviewTask t SET t.status = :claimed, t.assignedFmKeycloakId = :sub, t.claimedAt = :now
            WHERE t.id = :id AND t.assigneeRole = 'FINANCE_MANAGER'
              AND ((t.status IN :openStatuses AND t.assignedFmKeycloakId IS NULL)
                   OR (t.status = :claimed AND t.assignedFmKeycloakId = :sub))
            """)
    int tryClaim(
            @Param("id") Long id,
            @Param("sub") String sub,
            @Param("now") Instant now,
            @Param("claimed") ReviewTaskStatus claimed,
            @Param("openStatuses") Collection<ReviewTaskStatus> openStatuses);

    @EntityGraph(attributePaths = {"account", "account.user"})
    @Query(value = """
            SELECT t FROM ReviewTask t
            WHERE t.assigneeRole = 'ADMIN' AND t.status IN :statuses
            ORDER BY CASE WHEN t.status = com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus.ESCALATED THEN 0 ELSE 1 END, t.dueAt ASC
            """,
            countQuery = "SELECT count(t) FROM ReviewTask t WHERE t.assigneeRole = 'ADMIN' AND t.status IN :statuses")
    Page<ReviewTask> findAdminTasksEscalatedFirst(
            @Param("statuses") List<ReviewTaskStatus> statuses,
            Pageable pageable);
}