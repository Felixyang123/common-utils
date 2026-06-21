package com.lezai.threadpool.storage.localfile;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.exception.StorageException;
import com.lezai.threadpool.storage.ConcurrentMapStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 本地文件存储抽象基类
 * 仅提供文件操作和基础缓存管理，不涉及业务逻辑
 *
 * @param <T> 存储的实体类型
 */
@Slf4j
public abstract class AbstractLocalFileStorage<T> extends ConcurrentMapStorage<T> {

    protected final String storageDir;

    protected AbstractLocalFileStorage(String storageDir) {
        super(new ConcurrentHashMap<>());
        this.storageDir = storageDir;
        initStorage();
        loadAll();
    }

    protected void initStorage() {
        Path path = Paths.get(storageDir);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created storage directory: {}", storageDir);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to create storage directory: " + storageDir, e);
        }
    }

    protected void loadAll() {
        Path dir = Paths.get(storageDir);
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> path.toString().endsWith(getFileSuffix()))
                    .forEach(this::loadSingleFile);
            log.info("Loaded {} {} files from storage dir: {}", getCacheSize(), getStorageType(), storageDir);
        } catch (IOException e) {
            log.error("Failed to load {} from storage dir: {}", getStorageType(), storageDir, e);
        }
    }

    protected abstract String getStorageType();

    private void loadSingleFile(Path path) {
        try {
            loadFile(path);
        } catch (Exception e) {
            log.error("Failed to load {} file: {}", getStorageType(), path, e);
        }
    }

    protected abstract void loadFile(Path path);

    protected Path getStoragePath(String id) {
        return Paths.get(storageDir, id + getFileSuffix());
    }

    protected <R> R readFile(Path path, Class<R> clazz) {
        try {
            if (!Files.exists(path)) {
                return null;
            }
            String content = Files.readString(path);
            if (content.isBlank()) {
                return null;
            }
            return JSON.parseObject(content, clazz);
        } catch (IOException e) {
            throw new StorageException(String.format("Failed to read %s file: %s", getStorageType(), path), e);
        }
    }

    protected void writeFile(Path path, Object data) {
        try {
            Files.writeString(path, JSON.toJSONString(data));
        } catch (IOException e) {
            throw new StorageException("Failed to write file: " + path, e);
        }
    }

    protected void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + path, e);
        }
    }

    /**
     * 读取文件内容（如果存在且不为空），否则使用默认值
     *
     * @param path            文件路径
     * @param clazz           实体类
     * @param defaultSupplier 默认值提供者
     * @param <R>             实体类型
     * @return 实体对象
     */
    protected <R> R getOrBuildFile(Path path, Class<R> clazz, Supplier<R> defaultSupplier) {
        R file = readFile(path, clazz);
        if (file == null) {
            file = defaultSupplier.get();
        }
        return file;
    }

    protected abstract String getFileSuffix();

}
