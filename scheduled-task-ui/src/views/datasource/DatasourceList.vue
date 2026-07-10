<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { usePagination } from "@/composables/usePagination";
import {
  pageDatasource,
  deleteDatasource,
  testDatasource,
  syncDatasourceSchema,
  pageDatasourceSyncLogs,
  getSchemaDocContent,
} from "@/api/datasource";
import DatasourceForm from "./DatasourceForm.vue";
import type { DatasourceConfig, DatasourceSchemaSyncLog } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "数据源管理" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({ name: "" });
const formVisible = ref(false);
const formId = ref<number | undefined>(undefined);

// 正在执行同步的数据源 id 集合，用于按钮 loading/禁用，避免重复点击
const syncingIds = ref<Set<number>>(new Set());

// 同步记录抽屉
const logDrawerVisible = ref(false);
const logDrawerTitle = ref("");
const logDatasourceId = ref<number | undefined>(undefined);
const logLoading = ref(false);
const logRecords = ref<DatasourceSchemaSyncLog[]>([]);
const logTotal = ref(0);
const logCurrent = ref(1);
const logSize = ref(10);

// 数据字典内容查看弹窗
const docDialogVisible = ref(false);
const docDialogTitle = ref("");
const docContent = ref("");
const docLoading = ref(false);

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageDatasource(buildQuery(queryForm));
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
  queryForm.name = "";
  reset();
  loadPage();
};
const handleCreate = () => {
  formId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (row: DatasourceConfig) => {
  formId.value = row.id;
  formVisible.value = true;
};
const handleDelete = async (row: DatasourceConfig) => {
  await ElMessageBox.confirm("确认删除该数据源？", "提示", { type: "warning" });
  await deleteDatasource(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};
const handleTest = async (row: DatasourceConfig) => {
  const res = await testDatasource(row.id!);
  if (res.success) {
    ElMessage.success("连接成功");
  } else {
    const stageText =
      res.stage === "SSH"
        ? "SSH 连接失败"
        : res.stage === "DATABASE"
          ? "数据库连接失败"
          : "连接失败";
    ElMessage.error(`${stageText}：${res.message || "未知错误"}`);
  }
};

const handleSync = async (row: DatasourceConfig) => {
  try {
    await ElMessageBox.confirm(
      `确认同步数据源 "${row.name}" 的表结构？同步过程可能耗时较久。`,
      "提示",
      { type: "info" }
    );
  } catch {
    return;
  }
  const id = row.id!;
  syncingIds.value.add(id);
  try {
    const res = await syncDatasourceSchema(id);
    ElMessage.success(`同步成功，已生成数据字典：${res.title}`);
    // 同步完成后直接打开记录抽屉，便于查看本次结果
    openLogDrawer(row);
  } catch (e: any) {
    ElMessage.error(e?.message || "同步失败");
  } finally {
    syncingIds.value.delete(id);
  }
};

const openLogDrawer = (row: DatasourceConfig) => {
  logDatasourceId.value = row.id!;
  logDrawerTitle.value = `${row.name} - 同步记录`;
  logCurrent.value = 1;
  logDrawerVisible.value = true;
  loadSyncLogs();
};

const loadSyncLogs = async () => {
  if (logDatasourceId.value == null) return;
  logLoading.value = true;
  try {
    const res = await pageDatasourceSyncLogs(logDatasourceId.value, {
      current: logCurrent.value,
      size: logSize.value,
    });
    logRecords.value = res.records || [];
    logTotal.value = res.total || 0;
  } finally {
    logLoading.value = false;
  }
};

const handleLogPageChange = (c: number, s: number) => {
  logCurrent.value = c;
  logSize.value = s;
  loadSyncLogs();
};

const statusTag = (status?: string) => {
  switch (status) {
    case "SUCCESS":
      return { type: "success" as const, text: "成功" };
    case "FAIL":
      return { type: "danger" as const, text: "失败" };
    case "RUNNING":
      return { type: "warning" as const, text: "同步中" };
    default:
      return { type: "info" as const, text: status || "-" };
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

const renderMarkdown = (content: string) => {
  const raw = marked.parse(content || "", { breaks: true, gfm: true }) as string;
  return DOMPurify.sanitize(raw);
};

const viewDoc = async (row: DatasourceSchemaSyncLog) => {
  if (!row.docId || logDatasourceId.value == null) return;
  docDialogTitle.value = row.docTitle || "数据字典";
  docContent.value = "";
  docDialogVisible.value = true;
  docLoading.value = true;
  try {
    docContent.value = await getSchemaDocContent(logDatasourceId.value, row.docId);
  } catch (e: any) {
    ElMessage.error(e?.message || "读取数据字典失败");
    docDialogVisible.value = false;
  } finally {
    docLoading.value = false;
  }
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

onMounted(loadPage);
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="数据源名称">
            <el-input v-model="queryForm.name" placeholder="数据源名称" clearable />
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button
        type="primary"
        v-permission="'datasource:create'"
        @click="handleCreate"
        >新增数据源</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="dbType" label="类型" width="100" />
      <el-table-column prop="host" label="主机" min-width="140" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="databaseName" label="数据库" min-width="120" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
            row.status === 1 ? "启用" : "禁用"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'datasource:edit'"
            @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-permission="'datasource:edit'"
            @click="handleTest(row)"
            >测试</el-button
          >
          <el-button
            link
            type="warning"
            v-permission="'datasource:edit'"
            :loading="syncingIds.has(row.id!)"
            :disabled="syncingIds.has(row.id!)"
            @click="handleSync(row)"
            >同步</el-button
          >
          <el-button
            link
            type="info"
            v-permission="'datasource:view'"
            @click="openLogDrawer(row)"
            >记录</el-button
          >
          <el-button
            link
            type="danger"
            v-permission="'datasource:delete'"
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

    <DatasourceForm
      v-model:visible="formVisible"
      :id="formId"
      @success="onFormSuccess"
    />

    <el-drawer
      v-model="logDrawerVisible"
      :title="logDrawerTitle"
      size="860px"
      destroy-on-close
    >
      <el-table v-loading="logLoading" :data="logRecords" border stripe>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">{{
              statusTag(row.status).text
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tableCount" label="表数量" width="80" />
        <el-table-column label="耗时" width="110">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column label="结果 / 文档" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.status === 'FAIL'">{{
              row.errorMessage || "失败"
            }}</span>
            <span v-else>{{ row.docTitle || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!row.docId"
              @click="viewDoc(row)"
              >查看文档</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div class="log-pagination">
        <BasePagination
          :total="logTotal"
          :current="logCurrent"
          :size="logSize"
          @change="handleLogPageChange"
        />
      </div>
    </el-drawer>

    <el-dialog
      v-model="docDialogVisible"
      :title="docDialogTitle"
      width="900px"
      top="5vh"
    >
      <div v-loading="docLoading" class="doc-md" v-html="renderMarkdown(docContent)" />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.log-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.doc-md {
  min-height: 200px;
  max-height: 70vh;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
}

.doc-md :first-child {
  margin-top: 0;
}

.doc-md table {
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}

.doc-md th,
.doc-md td {
  border: 1px solid var(--el-border-color);
  padding: 6px 10px;
  text-align: left;
}

.doc-md th {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.doc-md pre {
  padding: 12px;
  background: #f6f8fa;
  border-radius: 6px;
  overflow-x: auto;
}

.doc-md code {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
}
</style>
