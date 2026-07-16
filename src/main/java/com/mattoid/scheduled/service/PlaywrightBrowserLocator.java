package com.mattoid.scheduled.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 定位 Playwright 本地缓存中的 Chromium 可执行文件，不创建 Playwright 实例，
 * 避免在依赖检测阶段触发任何驱动或浏览器下载。
 */
@Component
public class PlaywrightBrowserLocator {

    /**
     * 查找本地 Playwright Chromium 可执行文件。
     *
     * @return 可执行文件路径；未找到时返回 null
     */
    public Path findChromiumExecutable() {
        Path cacheDir = getPlaywrightCacheDir();
        if (cacheDir == null || !Files.exists(cacheDir)) {
            return null;
        }
        try (Stream<Path> dirs = Files.list(cacheDir)) {
            return dirs
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("chromium-"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .map(this::findExecutableInChromiumDir)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 判断 Chromium 可执行文件是否存在于本地缓存。
     */
    public boolean isChromiumPresent() {
        return findChromiumExecutable() != null;
    }

    private Path findExecutableInChromiumDir(Path chromiumDir) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        Path executable;
        if (os.contains("win")) {
            executable = chromiumDir.resolve("chrome-win/chrome.exe");
        } else if (os.contains("mac")) {
            executable = chromiumDir.resolve("chrome-mac/Chromium.app/Contents/MacOS/Chromium");
        } else {
            executable = chromiumDir.resolve("chrome-linux/chrome");
        }
        return isExecutable(executable, os) ? executable : null;
    }

    private boolean isExecutable(Path executable, String os) {
        if (Files.isExecutable(executable)) {
            return true;
        }
        if (os.contains("win")) {
            return Files.exists(executable) && executable.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe");
        }
        return false;
    }

    Path getPlaywrightCacheDir() {
        String home = System.getProperty("user.home");
        if (home == null) {
            return null;
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile == null) {
                return null;
            }
            return Paths.get(userProfile, "AppData", "Local", "ms-playwright");
        } else if (os.contains("mac")) {
            return Paths.get(home, "Library", "Caches", "ms-playwright");
        } else {
            return Paths.get(home, ".cache", "ms-playwright");
        }
    }
}
