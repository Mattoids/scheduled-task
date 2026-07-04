<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import { usePagination } from "@/composables/usePagination";
import { listTaskLogs, pageTaskLog } from "@/api/task";

interface Props {
  visible: boolean;
  taskId?: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  "update:visible": [value: boolean];
}>();

const { current, size, total, records, buildQuery, setPageResult } =
  usePagination();
const loading = ref(false);

const drawerVisible = computed({
  get: () => props.visible,
  set: (val) => emit("update:visible", val),
});

const loadPage = async () => {
  loading.value = true;
  try {
    if (props.taskId) {
      const res = await listTaskLogs(props.taskId, buildQuery());
      setPageResult(res);
    } else {
      const res = await pageTaskLog(buildQuery());
      setPageResult(res);
    }
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.visible,
  (val) => {
    if (val) {
      current.value = 1;
      loadPage();
      startAutoRefresh();
    } else {
      stopAutoRefresh();
    }
  },
);

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

const refreshInterval = ref(5);
let refreshTimer: ReturnType<typeof setInterval> | null = null;

const startAutoRefresh = () => {
  stopAutoRefresh();
  refreshTimer = setInterval(() => {
    loadPage();
  }, refreshInterval.value * 1000);
};

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
};

const handleIntervalChange = (seconds: number) => {
  refreshInterval.value = seconds;
  if (seconds > 0) {
    startAutoRefresh();
  } else {
    stopAutoRefresh();
  }
};

const intervalLabel = computed(() => {
  if (refreshInterval.value <= 0) return "停止刷新";
  if (refreshInterval.value < 60) return `${refreshInterval.value}秒`;
  return `${Math.floor(refreshInterval.value / 60)}分钟`;
});

const statusType = (status?: string) => {
  switch (status) {
    case "SUCCESS":
      return "success";
    case "FAILED":
      return "danger";
    case "RUNNING":
      return "warning";
    default:
      return "info";
  }
};
</script>

<template>
  <el-drawer v-model="drawerVisible" title="任务执行日志" size="60%">
    <div class="table-toolbar">
      <el-dropdown trigger="hover" @command="handleIntervalChange">
        <el-button :icon="Refresh" @click="loadPage"
          >刷新<span
            v-if="refreshInterval > 0"
            style="margin-left: 4px; color: #909399"
            >({{ intervalLabel }})</span
          ></el-button
        >
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :command="5">5秒</el-dropdown-item>
            <el-dropdown-item :command="10">10秒</el-dropdown-item>
            <el-dropdown-item :command="30">30秒</el-dropdown-item>
            <el-dropdown-item :command="60">1分钟</el-dropdown-item>
            <el-dropdown-item :command="0">停止刷新</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="triggerMode" label="触发方式" width="100">
        <template #default="{ row }">
          <el-tag :type="row.triggerMode === 'MANUAL' ? 'primary' : 'info'">{{
            row.triggerMode === "MANUAL" ? "手动" : "自动"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">
            {{
              row.status === "SUCCESS"
                ? "成功"
                : row.status === "FAILED"
                  ? "失败"
                  : "执行中"
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column prop="endTime" label="结束时间" width="170" />
      <el-table-column
        prop="resultMessage"
        label="结果"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="errorMessage"
        label="错误信息"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="filePath"
        label="文件路径"
        min-width="200"
        show-overflow-tooltip
      />
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />
  </el-drawer>
</template>
