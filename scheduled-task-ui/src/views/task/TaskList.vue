<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import {
  pageTask,
  deleteTask,
  updateTaskStatus,
  triggerTask,
  syncWeComMenu,
} from "@/api/task";
import TaskForm from "./TaskForm.vue";
import TaskLogDrawer from "./TaskLogDrawer.vue";
import type { TaskConfig } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "任务管理" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();

const queryForm = reactive({
  taskName: "",
  taskCode: "",
});

const loading = ref(false);
const formVisible = ref(false);
const formId = ref<number | undefined>(undefined);
const logDrawerVisible = ref(false);
const logTaskId = ref<number | undefined>(undefined);

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageTask(buildQuery(queryForm));
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
  queryForm.taskName = "";
  queryForm.taskCode = "";
  reset();
  loadPage();
};

const handleCreate = () => {
  formId.value = undefined;
  formVisible.value = true;
};

const handleEdit = (row: TaskConfig) => {
  formId.value = row.id;
  formVisible.value = true;
};

const handleDelete = async (row: TaskConfig) => {
  await ElMessageBox.confirm("确认删除该任务？", "提示", { type: "warning" });
  await deleteTask(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};

const handleStatusChange = async (row: TaskConfig) => {
  const status = row.status === "ENABLE" ? "DISABLE" : "ENABLE";
  await updateTaskStatus(row.id!, status);
  ElMessage.success("状态更新成功");
  loadPage();
};

const handleTrigger = async (row: TaskConfig) => {
  await triggerTask(row.id!);
  ElMessage.success("任务已开始执行");
};

const handleSyncWeComMenu = async () => {
  try {
    const res = await syncWeComMenu();
    const successCount = res.filter((item) => item.success).length;
    const failCount = res.length - successCount;
    if (failCount === 0) {
      ElMessage.success(`菜单同步成功，共 ${successCount} 个应用`);
    } else {
      ElMessage.warning(`菜单同步完成，成功 ${successCount} 个，失败 ${failCount} 个`);
    }
  } catch (e) {
    ElMessage.error("菜单同步失败");
  }
};

const handleLogs = (row: TaskConfig) => {
  logTaskId.value = row.id;
  logDrawerVisible.value = true;
};

const onFormSuccess = () => {
  formVisible.value = false;
  loadPage();
};

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

onMounted(() => {
  loadPage();
});
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="任务名称">
            <el-input
              v-model="queryForm.taskName"
              placeholder="任务名称"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="任务编码">
            <el-input
              v-model="queryForm.taskCode"
              placeholder="任务编码"
              clearable
            />
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button
        type="primary"
        v-permission="'task:create'"
        @click="handleCreate"
        >新增任务</el-button
      >
      <el-button
        type="success"
        v-permission="'task:edit'"
        @click="handleSyncWeComMenu"
        >同步菜单</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column
        prop="taskName"
        label="任务名称"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="taskCode"
        label="任务编码"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column prop="sortOrder" label="排序" width="90" align="center" />
      <el-table-column prop="inWecomMenu" label="应用菜单" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.inWecomMenu === 1" type="success">已加入</el-tag>
          <el-tag v-else type="info">未加入</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggerType" label="触发类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.triggerType === 'CRON'" type="primary">CRON</el-tag>
          <el-tag v-else type="info">单次</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="taskType" label="任务类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.taskType === 'CRAWL'" type="warning">网页爬取</el-tag>
          <el-tag v-else type="success">SQL</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="triggerConfig"
        label="触发配置"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column prop="status" label="状态" width="150">
        <template #default="{ row }">
          <el-switch
            v-permission="'task:edit'"
            :model-value="row.status"
            active-value="ENABLE"
            inactive-value="DISABLE"
            @change="handleStatusChange(row)"
          />
          <el-tag v-if="row.status === 'ENABLE'" type="success">启用</el-tag>
          <el-tag v-else type="danger">禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'task:edit'"
            @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-permission="'task:trigger'"
            @click="handleTrigger(row)"
            >立即执行</el-button
          >
          <el-button
            link
            type="primary"
            v-permission="'log:view'"
            @click="handleLogs(row)"
            >日志</el-button
          >
          <el-button
            link
            type="danger"
            v-permission="'task:delete'"
            @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />

    <TaskForm
      v-model:visible="formVisible"
      :id="formId"
      @success="onFormSuccess"
    />

    <TaskLogDrawer v-model:visible="logDrawerVisible" :task-id="logTaskId" />
  </div>
</template>
