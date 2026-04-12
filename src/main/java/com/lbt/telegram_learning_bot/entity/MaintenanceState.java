package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "maintenance_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "maintenance_enabled", nullable = false)
    private boolean maintenanceEnabled = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}