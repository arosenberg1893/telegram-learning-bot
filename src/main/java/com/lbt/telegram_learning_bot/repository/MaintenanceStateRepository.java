package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.MaintenanceState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceStateRepository extends JpaRepository<MaintenanceState, Long> {
}