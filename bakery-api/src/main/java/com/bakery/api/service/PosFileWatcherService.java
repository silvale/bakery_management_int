package com.bakery.api.service;
/**
 * @deprecated V12: File watcher removed. POS files vẫn được upload qua API endpoint,
 * nhưng không còn tự động watch thư mục nữa. Dùng PosFileProcessorService trực tiếp.
 */
@Deprecated(since = "V12", forRemoval = true)
public class PosFileWatcherService {}
