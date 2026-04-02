package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.repository.UserMistakeRepository;
import com.lbt.telegram_learning_bot.repository.UserProgressRepository;
import com.lbt.telegram_learning_bot.repository.UserStudyTimeRepository;
import com.lbt.telegram_learning_bot.repository.UserTestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProgressCleanupService {

    private final UserProgressRepository userProgressRepository;
    private final UserMistakeRepository userMistakeRepository;
    private final UserStudyTimeRepository userStudyTimeRepository;
    private final UserTestResultRepository userTestResultRepository;

    @Transactional
    public void deleteAllUserData(Long userId) {
        userProgressRepository.deleteByUserId(userId);
        userMistakeRepository.deleteByUserId(userId);
        userStudyTimeRepository.deleteByUserId(userId);
        userTestResultRepository.deleteByUserId(userId);
    }
}