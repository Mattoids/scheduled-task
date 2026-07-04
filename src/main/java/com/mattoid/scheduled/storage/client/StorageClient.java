package com.mattoid.scheduled.storage.client;

import java.io.File;

/**
 * 存储系统客户端抽象。
 */
public interface StorageClient {

    /**
     * 上传文件并返回访问 URL。
     *
     * @param file     待上传文件
     * @param filename 目标文件名（不含路径）
     * @return 可下载/读取的 URL
     */
    String upload(File file, String filename) throws Exception;

    /**
     * 删除远程文件（如果支持）。
     *
     * @param path 上传时返回的 URL 或相对路径
     */
    default void delete(String path) throws Exception {
    }
}
