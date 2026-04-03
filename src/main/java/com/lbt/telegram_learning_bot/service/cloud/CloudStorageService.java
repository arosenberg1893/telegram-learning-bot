package com.lbt.telegram_learning_bot.service.cloud;

public interface CloudStorageService {
    String upload(byte[] data, String fileName) throws Exception;
    String getName();
}