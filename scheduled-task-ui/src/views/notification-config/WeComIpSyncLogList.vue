<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { usePagination } from "@/composables/usePagination";
import { pageWeComIpSyncLogs } from "@/api/wecomIpSync";
import { listNotificationConfig } from "@/api/notificationConfig";
import type { NotificationConfig, WeComIpSyncLog } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "通知管理" }, { title: "IP 同步日志" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive<{ configId?: number; status?: string }>({
  configId: undefined,
  status: undefined,
});

const configOptions = ref<NotificationConfig[]>([]);

const statusOptions = [
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAIL" },
];

const loadConfigs = async () => {
  const res = await listNotificationConfig({ configType: "WECOM_APP" });
  configOptions.value = res.records || [];
};

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageWeComIpSyncLogs(buildQuery(queryForm));
    setPageResult(res);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  current.value = 1;
  loadPage();
};
const handleReset = () => {
  queryForm.configId = undefined;
  queryForm.status = undefined;
  reset();
  loadPage();
};

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

const statusTag = (status?: string) => {
  switch (status) {
    case "SUCCESS":
      return { type: "success" as const, text: "成功" };
    case "FAIL":
      return { type: "danger" as const, text: "失败" };
    default:
      return { type: "info" as const, text: status || "-" };
  }
};

const triggerText = (triggerType?: string) => {
  switch (triggerType) {
    case "MANUAL":
      return "手动";
    case "AUTO":
      return "自动";
    default:
      return triggerType || "-";
  }
};

const failReasonText = (reason?: string) => {
  switch (reason) {
    case "COOKIE_MISSING":
      return "Cookie 未配置";
    case "COOKIE_INVALID":
      return "Cookie 失效";
    case "IP_DETECT_FAIL":
      return "IP 检测/解析失败";
    case "CONFIG_NOT_FOUND":
      return "配置不存在";
    case "SYNC_FAIL":
      return "同步失败";
    default:
      return reason || "";
  }
};

const formatDuration = (ms?: number) => {
  if (ms == null) return "-";
  if (ms < 1000) return `${ms} ms`;
  const seconds = ms / 1000;
  if (seconds < 60) return `${seconds.toFixed(1)} 秒`;
  const m = Math.floor(seconds / 60);
  const s = Math.round(seconds % 60);
  return `${m} 分 ${s} 秒`;
};

onMounted(() => {
  loadConfigs();
  loadPage();
});
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="8">
          <el-form-item label="通知配置">
            <el-select
              v-model="queryForm.configId"
              placeholder="全部企业微信应用"
              clearable
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="cfg in configOptions"
                :key="cfg.id"
                :label="cfg.configName"
                :value="cfg.id!"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="同步状态">
            <el-select
              v-model="queryForm.status"
              placeholder="全部状态"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="configName" label="通知配置" min-width="140" show-overflow-tooltip />
      <el-table-column label="触发方式" width="90">
        <template #default="{ row }">{{ triggerText(row.triggerType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status).type" size="small">{{
            statusTag(row.status).text
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detectedIp" label="检测 IP" width="140" />
      <el-table-column prop="ipSource" label="IP 检测源" min-width="180" show-overflow-tooltip />
      <el-table-column label="可信 IP 变更" min-width="240" show-overflow-tooltip>
        <template #default="{ row }: { row: WeComIpSyncLog }">
          <span v-if="row.oldIps || row.newIps">{{ row.oldIps || "-" }} → {{ row.newIps || "-" }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" width="140">
        <template #default="{ row }">
          <span v-if="row.status === 'FAIL'" class="fail-text">{{ failReasonText(row.failReason) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="结果描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="startTime" label="同步时间" width="170" />
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />
  </div>
</template>

<style scoped lang="scss">
.fail-text {
  color: var(--el-color-danger);
}
</style>
