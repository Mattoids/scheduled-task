<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import { usePagination } from "@/composables/usePagination";
import { pageTaskLog } from "@/api/task";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "任务日志" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({
  status: "",
});

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

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageTaskLog(buildQuery(queryForm));
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
  queryForm.status = "";
  reset();
  loadPage();
};

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

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

onMounted(() => {
  loadPage();
  startAutoRefresh();
});

onUnmounted(stopAutoRefresh);
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="状态">
            <el-select v-model="queryForm.status" placeholder="全部" clearable>
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="执行中" value="RUNNING" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

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
      <el-table-column prop="taskId" label="任务 ID" width="90" />
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
  </div>
</template>
