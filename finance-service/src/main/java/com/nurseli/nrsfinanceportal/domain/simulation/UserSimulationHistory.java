package com.nurseli.nrsfinanceportal.domain.simulation;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Kullanıcının kaydettiği simülasyon geçmişi; tam sonuç payload'ı JSON olarak saklanır.
 */
@Entity
@Table(name = "user_simulation_history")
public class UserSimulationHistory {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;

    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserSimulationHistory() {
    }

    public static UserSimulationHistory of(
            String id,
            User user,
            String label,
            Instant savedAt,
            String amountCurrency,
            String itemsJson
    ) {
        UserSimulationHistory row = new UserSimulationHistory();
        row.id = id;
        row.user = user;
        row.label = label;
        row.savedAt = savedAt;
        row.amountCurrency = amountCurrency;
        row.itemsJson = itemsJson;
        row.createdAt = Instant.now();
        return row;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getLabel() {
        return label;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public String getAmountCurrency() {
        return amountCurrency;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
