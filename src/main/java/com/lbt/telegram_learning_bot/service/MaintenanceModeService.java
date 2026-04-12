package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.entity.MaintenanceState;
import com.lbt.telegram_learning_bot.repository.MaintenanceStateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для управления режимом обслуживания бота.
 * При включённом режиме обычные пользователи не могут взаимодействовать с ботом,
 * а администраторы получают доступ к операциям восстановления и резервного копирования.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceModeService {

    private final MaintenanceStateRepository maintenanceStateRepository;

    private MaintenanceState state;

    @PostConstruct
    public void init() {
        List<MaintenanceState> all = maintenanceStateRepository.findAll();
        if (all.isEmpty()) {
            state = new MaintenanceState();
            state.setMaintenanceEnabled(false);
            maintenanceStateRepository.save(state);
            log.info("Initialized maintenance mode: disabled");
        } else {
            state = all.get(0);
            log.info("Loaded maintenance mode: enabled={}", state.isMaintenanceEnabled());
        }
    }

    /**
     * Проверяет, включён ли режим обслуживания.
     */
    public boolean isMaintenance() {
        return state.isMaintenanceEnabled();
    }

    /**
     * Включает режим обслуживания.
     */
    @Transactional
    public void enableMaintenance() {
        if (!state.isMaintenanceEnabled()) {
            state.setMaintenanceEnabled(true);
            maintenanceStateRepository.save(state);
            log.info("Maintenance mode ENABLED");
        }
    }

    /**
     * Выключает режим обслуживания.
     */
    @Transactional
    public void disableMaintenance() {
        if (state.isMaintenanceEnabled()) {
            state.setMaintenanceEnabled(false);
            maintenanceStateRepository.save(state);
            log.info("Maintenance mode DISABLED");
        }
    }

    /**
     * Переключает режим обслуживания (включить/выключить).
     */
    @Transactional
    public void toggleMaintenance() {
        if (state.isMaintenanceEnabled()) {
            disableMaintenance();
        } else {
            enableMaintenance();
        }
    }
}