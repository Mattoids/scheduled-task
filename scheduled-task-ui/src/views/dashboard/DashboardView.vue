<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import { useRouter } from "vue-router";
import {
  Timer,
  Coin,
  Message,
  DocumentCopy,
  TrendCharts,
  CircleCheck,
  CircleClose,
  Plus,
  Refresh,
  Warning,
  ArrowRight,
  Loading,
} from "@element-plus/icons-vue";
import { useAppStore } from "@/stores/app";
import { getDashboardStats, getServerTime } from "@/api/dashboard";
import { installDependency, type InstallCompleteEvent } from "@/api/system";
import { useUserStore } from "@/stores/user";
import { usePermission } from "@/composables/usePermission";
import type { DashboardStats, RecentTaskLog } from "@/types";
import { ElMessage } from "element-plus";

const appStore = useAppStore();
const userStore = useUserStore();
const { has: hasPermission } = usePermission();
const router = useRouter();
appStore.setBreadcrumb([{ title: "首页" }]);

const stats = ref<DashboardStats>({
  taskCount: 0,
  datasourceCount: 0,
  notificationConfigCount: 0,
  templateCount: 0,
  todayLogCount: 0,
  successLogCount: 0,
  failedLogCount: 0,
  taskStatusStats: {},
  todayStatusStats: {},
  recentLogs: [],
});

const loading = ref(false);

const statCards = [
  {
    title: "任务总数",
    key: "taskCount" as keyof DashboardStats,
    icon: Timer,
    color: "blue",
    path: "/task",
  },
  {
    title: "数据源",
    key: "datasourceCount" as keyof DashboardStats,
    icon: Coin,
    color: "green",
    path: "/datasource",
  },
  {
    title: "通知配置",
    key: "notificationConfigCount" as keyof DashboardStats,
    icon: Message,
    color: "orange",
    path: "/notification/config",
  },
  {
    title: "报表模板",
    key: "templateCount" as keyof DashboardStats,
    icon: DocumentCopy,
    color: "red",
    path: "/template",
  },
];

const logCards = [
  {
    title: "今日执行",
    key: "todayLogCount" as keyof DashboardStats,
    icon: TrendCharts,
    color: "purple",
    path: "/task-log",
  },
  {
    title: "累计成功",
    key: "successLogCount" as keyof DashboardStats,
    icon: CircleCheck,
    color: "success",
    path: "/task-log",
  },
  {
    title: "累计失败",
    key: "failedLogCount" as keyof DashboardStats,
    icon: CircleClose,
    color: "danger",
    path: "/task-log",
  },
];

const quickActions = [
  { label: "新建任务", path: "/task", icon: Plus, type: "primary" },
  { label: "SQL 管理", path: "/task-sql", icon: DocumentCopy, type: "default" },
  { label: "数据源", path: "/datasource", icon: Coin, type: "default" },
];

const colorMap: Record<string, string> = {
  blue: "#3b82f6",
  green: "#10b981",
  orange: "#f59e0b",
  red: "#ef4444",
  purple: "#8b5cf6",
  success: "#10b981",
  danger: "#ef4444",
};

const statusTypeMap: Record<string, any> = {
  SUCCESS: "success",
  FAILED: "danger",
  RUNNING: "warning",
};

const statusLabelMap: Record<string, string> = {
  SUCCESS: "成功",
  FAILED: "失败",
  RUNNING: "执行中",
};

const triggerLabelMap: Record<string, string> = {
  MANUAL: "手动",
  AUTO: "自动",
};

const enabledTaskCount = computed(
  () => stats.value.taskStatusStats?.ENABLE ?? 0,
);
const disabledTaskCount = computed(
  () => stats.value.taskStatusStats?.DISABLE ?? 0,
);
const taskTotal = computed(
  () => enabledTaskCount.value + disabledTaskCount.value,
);
const enabledRate = computed(() =>
  taskTotal.value > 0
    ? Math.round((enabledTaskCount.value / taskTotal.value) * 100)
    : 0,
);

const todaySuccess = computed(() => stats.value.todayStatusStats?.SUCCESS ?? 0);
const todayFailed = computed(() => stats.value.todayStatusStats?.FAILED ?? 0);
const todayRunning = computed(() => stats.value.todayStatusStats?.RUNNING ?? 0);
const todayFinished = computed(() => todaySuccess.value + todayFailed.value);
const successRate = computed(() =>
  todayFinished.value > 0
    ? Math.round((todaySuccess.value / todayFinished.value) * 100)
    : 0,
);

const installDialogVisible = ref(false);
const installDialogTitle = ref("安装依赖");
const installProgress = ref(0);
const installProgressStatus = ref("");
const installLogs = ref<string[]>([]);
const installAbortController = ref<AbortController | null>(null);
const installingKey = ref<string | null>(null);

const isInstalling = computed(() => !!installAbortController.value);

const startInstall = (dep: DependencyStatusItem) => {
  if (!dep.installable || dep.available) return;
  if (!userStore.token) {
    ElMessage.error("登录信息已过期，请重新登录");
    return;
  }

  installDialogVisible.value = true;
  installDialogTitle.value = `安装 ${dep.name}`;
  installProgress.value = 0;
  installProgressStatus.value = "";
  installLogs.value = [];
  installingKey.value = dep.key;

  const controller = installDependency(dep.key, userStore.token, {
    onOpen: () => {
      installLogs.value.push("连接安装服务成功...");
    },
    onMessage: (event, data) => {
      if (event === "message") {
        const payload = data as { level?: string; message?: string };
        installLogs.value.push(payload.message || String(data));
      } else if (event === "progress") {
        const payload = data as { phase?: string; percentage?: number };
        if (payload.percentage != null) {
          installProgress.value = Math.min(100, Math.max(0, payload.percentage));
          installProgressStatus.value = payload.phase || "";
        }
      } else if (event === "phase") {
        const payload = data as { phase?: string; message?: string };
        if (payload.message) {
          installLogs.value.push(`[阶段] ${payload.message}`);
        }
      } else if (event === "info" || event === "command") {
        const payload = data as { message?: string; command?: string };
        if (payload.message) {
          installLogs.value.push(payload.message);
        }
        if (payload.command) {
          installLogs.value.push(`$ ${payload.command}`);
        }
      } else if (event === "complete") {
        const payload = data as InstallCompleteEvent;
        installProgress.value = 100;
        installProgressStatus.value = "completed";
        ElMessage.success(payload.message || "安装完成");
        if (payload.dependencies) {
          appStore.dependencies = payload.dependencies;
        } else {
          appStore.loadDependencies();
        }
        closeInstallDialogSoon();
      } else if (event === "error") {
        const payload = data as { message?: string };
        installProgressStatus.value = "exception";
        ElMessage.error(payload.message || "安装失败");
        closeInstallDialogSoon();
      }
    },
    onError: (error) => {
      installProgressStatus.value = "exception";
      installLogs.value.push(`[错误] ${error.message}`);
      ElMessage.error(error.message || "安装请求失败");
      closeInstallDialogSoon();
    },
    onClose: () => {
      installAbortController.value = null;
      installingKey.value = null;
    },
  });

  installAbortController.value = controller;
};

const closeInstallDialogSoon = () => {
  setTimeout(() => {
    installDialogVisible.value = false;
    installAbortController.value?.abort();
    installAbortController.value = null;
    installingKey.value = null;
  }, 1500);
};

const closeInstallDialog = () => {
  installAbortController.value?.abort();
  installAbortController.value = null;
  installingKey.value = null;
  installDialogVisible.value = false;
};

interface DependencyStatusItem {
  key: string
  name: string
  available: boolean | null
  message: string
  installable: boolean
}

const dependencyStatusList = computed<DependencyStatusItem[]>(() => {
  if (appStore.dependenciesLoading) {
    return [
      {
        name: "系统依赖检测中",
        available: null as boolean | null,
        message: "正在检测系统依赖，请稍候...",
        installable: false,
        key: "loading",
      },
    ];
  }
  if (appStore.dependencies.length === 0) {
    return [
      {
        name: "系统依赖检测失败",
        available: false,
        message: "无法获取系统依赖状态，企业微信相关功能不可用",
        installable: false,
        key: "error",
      },
    ];
  }
  return appStore.dependencies.map((d) => ({
    name: d.name,
    available: d.available,
    message: d.message,
    installable: d.installable,
    key: d.key,
  }));
});

const loadStats = async () => {
  loading.value = true;
  try {
    stats.value = await getDashboardStats();
  } finally {
    loading.value = false;
  }
};

const serverTime = ref("");
const serverTzLabel = ref("");
const serverTzName = ref("");
let serverTimeTimer: number | undefined;
// 同步基准点：performance.now() 与「此刻的服务器时间」配对，后续本地推进
let perfBaseMs = 0;
let serverBaseMs = 0;

/**
 * 把 epoch 毫秒按指定 IANA 时区格式化为 yyyy-MM-dd HH:mm:ss。
 * 使用 Intl 的 timeZone 选项，避免被浏览器本地时区污染。
 */
const formatInTimeZone = (ms: number, tz: string) => {
  try {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: tz,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    }).formatToParts(new Date(ms));
    const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "00";
    return `${get("year")}-${get("month")}-${get("day")} ${get("hour")}:${get("minute")}:${get("second")}`;
  } catch {
    return "";
  }
};

/** "+08:00" → "UTC+8" ； "+05:30" → "UTC+5:30" ； "Z"/"+00:00" → "UTC" */
const formatUtcLabel = (offset: string) => {
  if (!offset || offset === "Z" || offset === "+00:00" || offset === "-00:00") {
    return "UTC";
  }
  const sign = offset[0];
  const [h, m] = offset.slice(1).split(":");
  const hh = String(parseInt(h, 10));
  const mm = m === "00" ? "" : `:${m}`;
  return `UTC${sign}${hh}${mm}`;
};

/**
 * NTP 风格时钟同步：连续采样 N 次，每次记录 RTT，
 * 服务器时间 ≈ 响应中携带的时间戳 + RTT/2（假设上下行对称）。
 * 取 RTT 最小的那次作为基准（最小 RTT 排队延迟最少、最接近真实对称值），
 * 典型 LAN/DC 环境误差 < 50ms，满足 < 1s 要求。
 */
const syncServerTime = async () => {
  const SAMPLES = 3;
  let best:
    | { offset: number; rtt: number; tz: string; tzLabel: string; tzName: string }
    | null = null;

  for (let i = 0; i < SAMPLES; i++) {
    const t0 = performance.now();
    try {
      const data = await getServerTime();
      const t3 = performance.now();
      const rtt = t3 - t0;
      // 响应到达瞬间的服务器时间 ≈ 服务器时间戳 + 单程延迟（RTT/2）
      const serverAtReceive = data.serverTimeMillis + rtt / 2;
      // 同步基准：用单调时钟 performance.now() 避免客户端系统时间被调整的影响
      const offset = serverAtReceive - t3;
      if (!best || rtt < best.rtt) {
        best = {
          offset,
          rtt,
          tz: data.timeZone,
          tzLabel: formatUtcLabel(data.utcOffset),
          tzName: data.timeZone,
        };
      }
    } catch {
      // 单次采样失败忽略，继续下一次
    }
  }

  if (!best) return;
  perfBaseMs = performance.now();
  serverBaseMs = perfBaseMs + best.offset;
  serverTzLabel.value = best.tzLabel;
  serverTzName.value = best.tzName;
  serverTime.value = formatInTimeZone(serverBaseMs, best.tz);
};

const tickServerTime = () => {
  if (!serverBaseMs || !serverTzName.value) return;
  const now = serverBaseMs + (performance.now() - perfBaseMs);
  serverTime.value = formatInTimeZone(now, serverTzName.value);
};

const handleRefresh = () => {
  loadStats();
  syncServerTime();
};

const goTo = (path: string) => {
  router.push(path);
};

const formatDuration = (row: RecentTaskLog) => {
  if (!row.startTime || !row.endTime) return "-";
  const start = new Date(row.startTime).getTime();
  const end = new Date(row.endTime).getTime();
  const seconds = Math.round((end - start) / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const rem = seconds % 60;
  return `${minutes}m ${rem}s`;
};

onMounted(() => {
  loadStats();
  syncServerTime();
  serverTimeTimer = window.setInterval(tickServerTime, 1000);
});

onUnmounted(() => {
  if (serverTimeTimer) {
    window.clearInterval(serverTimeTimer);
    serverTimeTimer = undefined;
  }
});
</script>

<template>
  <div v-loading="loading" class="page-card dashboard-page">
    <div class="dashboard-welcome">
      <div class="welcome-text">
        <h1 class="welcome-title">欢迎使用定时任务报表系统</h1>
        <p class="welcome-desc">自动化报表生成、定时调度与邮件分发一站式平台</p>
        <p class="welcome-server-time" :title="serverTzName || undefined">
          <el-icon class="server-time-icon"><Timer /></el-icon>
          <span v-if="serverTzLabel" class="server-time-tz">{{ serverTzLabel }}</span>
          <span class="server-time-sep">·</span>
          <span class="server-time-value">{{ serverTime || "--" }}</span>
        </p>
      </div>
      <div class="quick-actions">
        <el-button
          v-for="action in quickActions"
          :key="action.label"
          :type="action.type as any"
          :icon="action.icon"
          @click="goTo(action.path)"
        >
          {{ action.label }}
        </el-button>
        <el-button :icon="Refresh" @click="handleRefresh">刷新</el-button>
      </div>
    </div>

    <div class="section-title">资源概览</div>
    <el-row :gutter="20">
      <el-col
        v-for="item in statCards"
        :key="item.title"
        :span="6"
        :xs="24"
        :sm="12"
        :md="6"
      >
        <el-card
          class="stat-card clickable"
          shadow="never"
          @click="goTo(item.path)"
        >
          <div class="stat-content">
            <div
              class="stat-icon"
              :style="{
                backgroundColor: colorMap[item.color] + '15',
                color: colorMap[item.color],
              }"
            >
              <el-icon :size="28"><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                {{ (stats[item.key] as number) ?? 0 }}
              </div>
              <div class="stat-label">{{ item.title }}</div>
            </div>
            <el-icon class="stat-arrow" :size="18"><ArrowRight /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title" style="margin-top: 28px">系统环境</div>
    <el-row :gutter="20">
      <el-col
        v-for="dep in dependencyStatusList"
        :key="dep.name"
        :span="8"
        :xs="24"
        :sm="24"
        :md="8"
      >
        <el-card class="env-card" shadow="never">
          <div class="env-content">
            <div
              class="env-dot"
              :style="{
                backgroundColor:
                  (dep.available === true
                    ? '#10b981'
                    : dep.available === false
                      ? '#ef4444'
                      : '#909399') + '20',
                color:
                  dep.available === true
                    ? '#10b981'
                    : dep.available === false
                      ? '#ef4444'
                      : '#909399',
              }"
            >
              <el-icon :size="24">
                <component
                  :is="
                    dep.available === true
                      ? CircleCheck
                      : dep.available === false
                        ? CircleClose
                        : Loading
                  "
                />
              </el-icon>
            </div>
            <div class="env-info">
              <div class="env-title">{{ dep.name }}</div>
              <div
                class="env-desc"
                :style="{
                  color:
                    dep.available === true
                      ? '#10b981'
                      : dep.available === false
                        ? '#ef4444'
                        : '#909399',
                }"
              >
                {{ dep.message }}
              </div>
            </div>
            <el-button
              v-if="dep.installable && !dep.available && hasPermission('system:user')"
              type="primary"
              size="small"
              :loading="installingKey === dep.key"
              :disabled="isInstalling"
              @click="startInstall(dep)"
            >
              安装
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title" style="margin-top: 28px">执行统计</div>
    <el-row :gutter="20">
      <el-col :span="8" :xs="24" :sm="24" :md="8">
        <el-card
          class="stat-card log-stat-card clickable"
          shadow="never"
          @click="goTo('/task-log')"
        >
          <div class="stat-content">
            <div
              class="stat-icon"
              :style="{
                backgroundColor: colorMap.purple + '15',
                color: colorMap.purple,
              }"
            >
              <el-icon :size="28"><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayLogCount }}</div>
              <div class="stat-label">今日执行</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8" :xs="24" :sm="24" :md="8">
        <el-card
          class="stat-card log-stat-card clickable"
          shadow="never"
          @click="goTo('/task-log')"
        >
          <div class="stat-content">
            <div
              class="stat-icon"
              :style="{
                backgroundColor: colorMap.success + '15',
                color: colorMap.success,
              }"
            >
              <el-icon :size="28"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.successLogCount }}</div>
              <div class="stat-label">累计成功</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8" :xs="24" :sm="24" :md="8">
        <el-card
          class="stat-card log-stat-card clickable"
          shadow="never"
          @click="goTo('/task-log')"
        >
          <div class="stat-content">
            <div
              class="stat-icon"
              :style="{
                backgroundColor: colorMap.danger + '15',
                color: colorMap.danger,
              }"
            >
              <el-icon :size="28"><CircleClose /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.failedLogCount }}</div>
              <div class="stat-label">累计失败</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12" :xs="24" :md="12">
        <el-card class="distribution-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>任务启用情况</span
              ><el-tag v-if="todayRunning > 0" type="warning" effect="dark"
                ><el-icon><Warning /></el-icon> 有
                {{ todayRunning }} 个任务正在执行</el-tag
              >
            </div>
          </template>
          <div class="distribution-body">
            <div class="distribution-value">{{ enabledRate }}%</div>
            <el-progress
              :percentage="enabledRate"
              :stroke-width="16"
              :show-text="false"
              status="success"
              class="distribution-progress"
            />
            <div class="distribution-detail">
              <span>已启用 {{ enabledTaskCount }}</span
              ><span>已停用 {{ disabledTaskCount }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12" :xs="24" :md="12">
        <el-card class="distribution-card" shadow="never">
          <template #header>
            <div class="card-header"><span>今日执行成功率</span></div>
          </template>
          <div class="distribution-body">
            <div
              class="distribution-value"
              :style="{
                color:
                  successRate >= 90
                    ? '#10b981'
                    : successRate >= 70
                      ? '#f59e0b'
                      : '#ef4444',
              }"
            >
              {{ successRate }}%
            </div>
            <el-progress
              :percentage="successRate"
              :stroke-width="16"
              :show-text="false"
              :status="
                successRate >= 90
                  ? 'success'
                  : successRate >= 70
                    ? 'warning'
                    : 'exception'
              "
              class="distribution-progress"
            />
            <div class="distribution-detail">
              <span>成功 {{ todaySuccess }}</span
              ><span>失败 {{ todayFailed }}</span
              ><span v-if="todayRunning > 0">执行中 {{ todayRunning }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="recent-log-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>最近执行日志</span
          ><el-button link type="primary" @click="goTo('/task-log')"
            >查看更多 <el-icon><ArrowRight /></el-icon
          ></el-button>
        </div>
      </template>
      <el-table
        :data="stats.recentLogs"
        size="small"
        stripe
        style="width: 100%"
      >
        <el-table-column
          prop="taskName"
          label="任务名称"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="触发方式" width="90">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.triggerMode === 'MANUAL' ? 'primary' : 'info'"
              >{{ triggerLabelMap[row.triggerMode] ?? row.triggerMode }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTypeMap[row.status]">{{
              statusLabelMap[row.status] ?? row.status
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">{{ formatDuration(row) }}</template>
        </el-table-column>
        <el-table-column
          prop="resultMessage"
          label="结果"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ row.startTime }}</template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="stats.recentLogs.length === 0"
        description="暂无执行记录"
      />
    </el-card>
    <el-dialog
      v-model="installDialogVisible"
      :title="installDialogTitle"
      width="600px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      @close="closeInstallDialog"
    >
      <div class="install-progress-body">
        <el-progress
          :percentage="installProgress"
          :status="installProgressStatus as any"
          :stroke-width="16"
          :format="(p: number) => `${p.toFixed(0)}%`"
        />
        <div class="install-log">
          <div
            v-for="(log, index) in installLogs"
            :key="index"
            class="install-log-line"
          >
            {{ log }}
          </div>
          <div v-if="installLogs.length === 0" class="install-log-empty">
            等待安装服务响应...
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.dashboard-page {
  padding: 28px;
}

.dashboard-welcome {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 8px;
}

.welcome-desc {
  color: #6b7280;
  font-size: 15px;
}

.welcome-server-time {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 5px 14px;
  border-radius: 6px;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  color: #1e1b4b;
  font-size: 14px;
  letter-spacing: 0.3px;
}

.server-time-icon {
  color: #6366f1;
  font-size: 14px;
}

.server-time-tz {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 4px;
  background: #6366f1;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.server-time-sep {
  color: #a5b4fc;
  font-weight: 300;
}

.server-time-value {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-weight: 600;
  color: #312e81;
  font-variant-numeric: tabular-nums;
}

.quick-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 16px;
  padding-left: 10px;
  border-left: 4px solid #4f46e5;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #ffffff 0%, #f9fafb 100%);
  transition: all 0.25s ease;
  margin-bottom: 20px;
}

.stat-card.clickable {
  cursor: pointer;
}

.stat-card.clickable:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1);
  border-color: #c7d2fe;
}

.stat-card :deep(.el-card__body) {
  padding: 20px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #6b7280;
  margin-top: 4px;
}

.stat-arrow {
  margin-left: auto;
  color: #d1d5db;
  transition: color 0.2s ease;
}

.stat-card.clickable:hover .stat-arrow {
  color: #4f46e5;
}

.log-stat-card {
  background: linear-gradient(135deg, #ffffff 0%, #faf5ff 100%);
}

.distribution-card,
.recent-log-card {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.recent-log-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.distribution-body {
  text-align: center;
  padding: 12px 8px;
}

.distribution-value {
  font-size: 42px;
  font-weight: 700;
  color: #10b981;
  margin-bottom: 12px;
}

.distribution-progress {
  margin-bottom: 12px;
}

.distribution-detail {
  display: flex;
  justify-content: center;
  gap: 24px;
  color: #6b7280;
  font-size: 14px;
}

.env-card {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #ffffff 0%, #f9fafb 100%);
  margin-bottom: 20px;
}

.env-card :deep(.el-card__body) {
  padding: 18px 20px;
}

.env-content {
  display: flex;
  align-items: center;
  gap: 14px;
}

.env-dot {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.env-title {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 4px;
}

.env-desc {
  font-size: 15px;
  font-weight: 600;
}

.env-info {
  flex: 1;
  min-width: 0;
}

.install-progress-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.install-log {
  max-height: 300px;
  overflow-y: auto;
  padding: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.install-log-line {
  white-space: pre-wrap;
  word-break: break-all;
  color: #374151;
}

.install-log-empty {
  color: #9ca3af;
  text-align: center;
  padding: 20px 0;
}

@media (max-width: 768px) {
  .dashboard-welcome {
    flex-direction: column;
  }

  .stat-card {
    margin-bottom: 16px;
  }

  .distribution-detail {
    gap: 12px;
    flex-wrap: wrap;
  }
}
</style>
