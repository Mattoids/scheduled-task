<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { usePagination } from "@/composables/usePagination";
import {
  listDatasource,
  pageAllDatasourceSyncLogs,
  getSchemaDocContent,
} from "@/api/datasource";
import type { DatasourceConfig, DatasourceSchemaSyncLog } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "数据源管理" }, { title: "同步日志" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive<{ datasourceId?: number }>({ datasourceId: undefined });

const datasourceOptions = ref<DatasourceConfig[]>([]);

const docDialogVisible = ref(false);
const docDialogTitle = ref("");
const docContent = ref("");
const docLoading = ref(false);

const loadDatasources = async () => {
  const res = await listDatasource();
  datasourceOptions.value = res.records || [];
};

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageAllDatasourceSyncLogs(buildQuery(queryForm));
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
  queryForm.datasourceId = undefined;
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
  if (!row.docId || !row.datasourceId) return;
  docDialogTitle.value = row.docTitle || "数据字典";
  docContent.value = "";
  docDialogVisible.value = true;
  docLoading.value = true;
  try {
    docContent.value = await getSchemaDocContent(row.datasourceId, row.docId);
  } catch (e: any) {
    ElMessage.error(e?.message || "读取数据字典失败");
    docDialogVisible.value = false;
  } finally {
    docLoading.value = false;
  }
};

onMounted(() => {
  loadDatasources();
  loadPage();
});
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="8">
          <el-form-item label="数据源">
            <el-select
              v-model="queryForm.datasourceId"
              placeholder="全部数据源"
              clearable
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="ds in datasourceOptions"
                :key="ds.id"
                :label="ds.name"
                :value="ds.id!"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="datasourceName" label="数据源" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status).type" size="small">{{
            statusTag(row.status).text
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tableCount" label="表数量" width="90" />
      <el-table-column label="耗时" width="110">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column prop="endTime" label="结束时间" width="170" />
      <el-table-column label="结果 / 文档" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.status === 'FAIL'" class="fail-text">{{
            row.errorMessage || "失败"
          }}</span>
          <span v-else>{{ row.docTitle || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
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

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />

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
.fail-text {
  color: var(--el-color-danger);
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
