package com.mattoid.scheduled.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 系统依赖项手动安装服务。
 * <p>根据运行操作系统自动选择安装命令，目前支持 Chromium/Playwright 浏览器及其系统依赖的半自动安装。
 * 安装过程通过 SSE 流式返回进度与日志，安装完成后触发依赖重检。</p>
 */
@Slf4j
@Service
public class DependencyInstallService {

    /** 当前支持安装 chromium 及其缺失的系统库 */
    private static final Set<String> SUPPORTED_KEYS = Set.of("chromium");

    private static boolean isSupportedKey(String key) {
        return SUPPORTED_KEYS.contains(key) || key.startsWith("lib:");
    }

    private static final long EMITTER_TIMEOUT_MS = 0L;
    private static final int PROCESS_TIMEOUT_MINUTES = 30;

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)%");

    private final DependencyCheckService dependencyCheckService;
    private final Map<String, InstallTask> runningTasks = new ConcurrentHashMap<>();

    public DependencyInstallService(DependencyCheckService dependencyCheckService) {
        this.dependencyCheckService = dependencyCheckService;
    }

    /**
     * 启动指定依赖项的安装流程，返回 SSE 流用于接收进度。
     *
     * @param key 依赖项 key，目前仅支持 "chromium"
     * @return SSE 流
     * @throws IllegalStateException    如果该依赖项正在安装中
     * @throws IllegalArgumentException 如果 key 不支持
     */
    public SseEmitter install(String key) {
        if (!isSupportedKey(key)) {
            throw new IllegalArgumentException("暂不支持的依赖项: " + key + "，目前仅支持 chromium 及其系统库");
        }
        String normalizedKey = "chromium";

        InstallTask task = new InstallTask(normalizedKey);
        InstallTask existing = runningTasks.putIfAbsent(normalizedKey, task);
        if (existing != null) {
            throw new IllegalStateException("chromium 正在安装中，请勿重复操作");
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        task.setEmitter(emitter);

        emitter.onCompletion(() -> cleanup(normalizedKey, task));
        emitter.onTimeout(() -> cleanup(normalizedKey, task));
        emitter.onError((e) -> cleanup(normalizedKey, task));

        CompletableFuture.runAsync(() -> {
            try {
                doInstall(task);
                task.sendEvent("complete", Map.of(
                        "success", true,
                        "message", "安装完成",
                        "dependencies", dependencyCheckService.refreshDependencies()
                ));
            } catch (Exception e) {
                log.error("[DependencyInstall] 安装 {} 失败", normalizedKey, e);
                task.sendEvent("error", Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            } finally {
                task.complete();
                runningTasks.remove(normalizedKey);
            }
        });

        return emitter;
    }

    /**
     * 查询指定依赖项是否正在安装。
     */
    public boolean isInstalling(String key) {
        String normalizedKey = isSupportedKey(key) ? "chromium" : key;
        InstallTask task = runningTasks.get(normalizedKey);
        return task != null && task.isRunning();
    }

    private void cleanup(String key, InstallTask task) {
        task.destroyProcess();
        runningTasks.remove(key, task);
    }

    private void doInstall(InstallTask task) throws Exception {
        OsInfo os = detectOs();
        task.sendEvent("info", Map.of(
                "osFamily", os.family(),
                "distro", os.distro(),
                "packageManager", os.packageManager(),
                "message", "检测到操作系统: " + os.description()
        ));

        // 1. 安装 Chromium 浏览器（Playwright 自动匹配当前平台）
        task.sendEvent("phase", Map.of("phase", "browser", "message", "开始下载 Chromium 浏览器..."));
        runPlaywrightCli(task, "install", "chromium");

        // 2. 安装系统依赖（Playwright 在支持的 Linux 发行版上可自动安装）
        task.sendEvent("phase", Map.of("phase", "deps", "message", "开始安装系统依赖..."));
        int depsExit = runPlaywrightCli(task, "install-deps", "chromium");

        // 3. 若 Playwright 的 deps 安装失败（不支持的发行版或权限不足），尝试用本机包管理器兜底
        if (depsExit != 0) {
            task.sendEvent("phase", Map.of("phase", "fallback", "message", "Playwright 自动依赖安装失败，尝试使用系统包管理器..."));
            boolean fallbackOk = tryFallbackSystemDepsInstall(os, task);
            if (!fallbackOk) {
                throw new RuntimeException("系统依赖安装失败，请根据日志手动安装缺失的库");
            }
        }
    }

    private int runPlaywrightCli(InstallTask task, String... args) throws Exception {
        List<String> command = buildPlaywrightCliCommand(args);
        task.sendEvent("command", Map.of("command", String.join(" ", command)));
        return execute(command, task, String.join("-", args));
    }

    /**
     * 构造调用 Playwright CLI 的命令。
     * 生产环境（Spring Boot fat jar）通过 loader.main 启动 CLI；
     * 开发环境（target/classes）使用 java.class.path。
     */
    private List<String> buildPlaywrightCliCommand(String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        URL codeSource = getClass().getProtectionDomain().getCodeSource().getLocation();
        File codeFile = new File(codeSource.toURI());

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);

        if (codeFile.isFile() && codeFile.getName().endsWith(".jar")) {
            // Spring Boot fat jar: 通过 loader.main 指定 Playwright CLI 入口
            cmd.add("-Dloader.main=com.microsoft.playwright.CLI");
            cmd.add("-jar");
            cmd.add(codeFile.getAbsolutePath());
        } else {
            // IDE / exploded: 使用完整 classpath
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("com.microsoft.playwright.CLI");
        }
        cmd.addAll(Arrays.asList(args));
        return cmd;
    }

    /**
     * 使用系统包管理器兜底安装常见 Chromium 运行库。
     * 仅对已知包管理器做最佳-effort 支持。
     */
    private boolean tryFallbackSystemDepsInstall(OsInfo os, InstallTask task) {
        String pm = os.packageManager();
        if (pm == null || "unknown".equals(pm)) {
            task.sendEvent("message", Map.of("level", "warn", "message", "无法识别系统包管理器，跳过兜底安装"));
            return false;
        }

        List<String> packages = switch (pm) {
            case "apt" -> List.of(
                    "libglib2.0-0", "libnss3", "libnspr4", "libdbus-1-3",
                    "libatk1.0-0", "libatk-bridge2.0-0", "libatspi2.0-0",
                    "libx11-6", "libxcomposite1", "libxdamage1", "libxext6", "libxfixes3",
                    "libxrandr2", "libgbm1", "libdrm2", "libxcb1", "libxkbcommon0", "libasound2",
                    "libcairo2", "libcups2", "libpango-1.0-0", "fonts-noto-cjk"
            );
            case "dnf", "yum" -> List.of(
                    "glib2", "nss", "nspr", "dbus", "atk", "at-spi2-atk", "at-spi2-core",
                    "libX11", "libXcomposite", "libXdamage", "libXext", "libXfixes",
                    "libXrandr", "mesa-libgbm", "libdrm", "libxcb", "xkeyboard-config", "alsa-lib",
                    "cairo", "cups-libs", "pango", "google-noto-sans-cjk-ttc-fonts"
            );
            case "apk" -> List.of(
                    "chromium", "chromium-chromedriver", "nss", "freetype", "harfbuzz",
                    "ca-certificates", "ttf-freefont"
            );
            case "pacman" -> List.of(
                    "chromium", "nss", "alsa-lib", "freetype2", "ttf-font"
            );
            case "zypper" -> List.of(
                    "chromium", "mozilla-nss", "mozilla-nspr", "libatk-1_0-0", "libatk-bridge-2_0-0",
                    "libatspi0", "libX11-6", "libXcomposite1", "libXdamage1", "libXext6",
                    "libXfixes3", "libXrandr2", "libgbm1", "libdrm2", "libxcb1", "libxkbcommon0",
                    "libasound2", "libcairo2", "libcups2", "libpango-1.0-0"
            );
            case "brew" -> List.of("chromium");
            default -> null;
        };

        if (packages == null) {
            task.sendEvent("message", Map.of("level", "warn", "message", "暂不支持通过 " + pm + " 自动安装依赖"));
            return false;
        }

        List<String> command = buildPackageManagerCommand(pm, packages);
        task.sendEvent("command", Map.of("command", String.join(" ", command)));
        try {
            int exit = execute(command, task, "fallback-" + pm);
            return exit == 0;
        } catch (Exception e) {
            task.sendEvent("message", Map.of("level", "error", "message", "兜底安装异常: " + e.getMessage()));
            return false;
        }
    }

    private List<String> buildPackageManagerCommand(String pm, List<String> packages) {
        String joined = String.join(" ", packages);
        return switch (pm) {
            case "apt" -> {
                // apt 需要先 update 再 install
                String script = "apt-get update && apt-get install -y --no-install-recommends " + joined;
                yield List.of("sh", "-c", script);
            }
            case "dnf" -> {
                List<String> cmd = new ArrayList<>(List.of("dnf", "install", "-y"));
                cmd.addAll(packages);
                yield cmd;
            }
            case "yum" -> {
                List<String> cmd = new ArrayList<>(List.of("yum", "install", "-y"));
                cmd.addAll(packages);
                yield cmd;
            }
            case "apk" -> {
                List<String> cmd = new ArrayList<>(List.of("apk", "add", "--no-cache"));
                cmd.addAll(packages);
                yield cmd;
            }
            case "pacman" -> {
                List<String> cmd = new ArrayList<>(List.of("pacman", "-Sy", "--noconfirm"));
                cmd.addAll(packages);
                yield cmd;
            }
            case "zypper" -> {
                List<String> cmd = new ArrayList<>(List.of("zypper", "install", "-y"));
                cmd.addAll(packages);
                yield cmd;
            }
            case "brew" -> {
                List<String> cmd = new ArrayList<>(List.of("brew", "install"));
                cmd.addAll(packages);
                yield cmd;
            }
            default -> throw new IllegalStateException("未知包管理器: " + pm);
        };
    }

    /**
     * 执行外部命令并流式输出日志与进度。
     */
    private int execute(List<String> command, InstallTask task, String phase) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        // 将 HOME 透传给子进程，避免 Playwright 找不到缓存目录
        pb.environment().putIfAbsent("HOME", System.getProperty("user.home"));
        Process process = pb.start();
        task.setProcess(process);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                task.sendEvent("message", Map.of("level", "info", "phase", phase, "message", line));
                Matcher m = PROGRESS_PATTERN.matcher(line);
                if (m.find()) {
                    try {
                        double pct = Double.parseDouble(m.group(1));
                        task.sendEvent("progress", Map.of("phase", phase, "percentage", pct));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("安装进程执行超时（" + PROCESS_TIMEOUT_MINUTES + " 分钟）");
        }
        return process.exitValue();
    }

    /**
     * 检测当前操作系统与包管理器信息。
     */
    private OsInfo detectOs() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String family;
        if (osName.contains("win")) {
            family = "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            family = "macos";
        } else if (osName.contains("linux")) {
            family = "linux";
        } else {
            family = "unknown";
        }

        String distro = "unknown";
        String packageManager = "unknown";
        String version = "";

        if ("linux".equals(family)) {
            Map<String, String> osRelease = readOsRelease();
            distro = osRelease.getOrDefault("ID", "unknown").toLowerCase(Locale.ROOT);
            version = osRelease.getOrDefault("VERSION_ID", "");
            packageManager = detectLinuxPackageManager(distro);
        } else if ("macos".equals(family)) {
            packageManager = commandExists("brew") ? "brew" : "unknown";
        } else if ("windows".equals(family)) {
            if (commandExists("winget")) {
                packageManager = "winget";
            } else if (commandExists("choco")) {
                packageManager = "choco";
            }
        }

        return new OsInfo(family, distro, packageManager, version);
    }

    private Map<String, String> readOsRelease() {
        Map<String, String> map = new LinkedHashMap<>();
        List<Path> candidates = List.of(
                Paths.get("/etc/os-release"),
                Paths.get("/usr/lib/os-release")
        );
        for (Path path : candidates) {
            if (!Files.exists(path)) {
                continue;
            }
            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    int idx = line.indexOf('=');
                    if (idx <= 0) {
                        continue;
                    }
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    map.put(key, value);
                }
            } catch (Exception e) {
                log.debug("读取 os-release 失败: {}", e.getMessage());
            }
        }
        return map;
    }

    private String detectLinuxPackageManager(String distro) {
        // 优先根据已安装命令判断
        if (commandExists("apt-get") || commandExists("apt")) {
            return "apt";
        }
        if (commandExists("dnf")) {
            return "dnf";
        }
        if (commandExists("yum")) {
            return "yum";
        }
        if (commandExists("apk")) {
            return "apk";
        }
        if (commandExists("pacman")) {
            return "pacman";
        }
        if (commandExists("zypper")) {
            return "zypper";
        }
        // 再根据发行版名兜底
        return switch (distro) {
            case "ubuntu", "debian", "linuxmint", "pop", "elementary" -> "apt";
            case "fedora" -> "dnf";
            case "centos", "rhel", "ol", "rocky", "almalinux" -> "yum";
            case "alpine" -> "apk";
            case "arch", "manjaro", "endeavouros" -> "pacman";
            case "opensuse", "opensuse-leap", "opensuse-tumbleweed", "sles" -> "zypper";
            default -> "unknown";
        };
    }

    private boolean commandExists(String command) {
        String[] paths = System.getenv("PATH").split(File.pathSeparator);
        for (String dir : paths) {
            File file = new File(dir, command);
            if (file.isFile() && file.canExecute()) {
                return true;
            }
            // Windows
            if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
                for (String ext : List.of(".exe", ".cmd", ".bat")) {
                    File winFile = new File(dir, command + ext);
                    if (winFile.isFile() && winFile.canExecute()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record OsInfo(String family, String distro, String packageManager, String version) {
        String description() {
            return family + ("unknown".equals(distro) ? "" : " / " + distro) +
                    (version.isBlank() ? "" : " " + version);
        }
    }

    /**
     * 单次安装任务，封装 SSE 发送与进程管理。
     */
    private static class InstallTask {
        private final String key;
        private volatile SseEmitter emitter;
        private volatile Process process;
        private volatile boolean running = true;

        InstallTask(String key) {
            this.key = key;
        }

        void setEmitter(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void setProcess(Process process) {
            this.process = process;
        }

        boolean isRunning() {
            return running;
        }

        void sendEvent(String eventName, Object data) {
            SseEmitter e = this.emitter;
            if (e == null) {
                return;
            }
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON);
                e.send(builder);
            } catch (Exception ex) {
                log.debug("[DependencyInstall] 发送 SSE 事件失败: {}", ex.getMessage());
            }
        }

        void complete() {
            this.running = false;
            destroyProcess();
            SseEmitter e = this.emitter;
            if (e != null) {
                try {
                    e.complete();
                } catch (Exception ignored) {
                }
            }
        }

        void destroyProcess() {
            Process p = this.process;
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }
}
