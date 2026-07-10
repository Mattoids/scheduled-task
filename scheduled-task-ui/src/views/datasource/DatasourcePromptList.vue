<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import { pageDatasource, updateDatasourcePrompt } from "@/api/datasource";
import type { DatasourceConfig } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "数据源管理" }, { title: "自定义 prompt" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({ name: "" });

const drawerVisible = ref(false);
const saving = ref(false);
const editingId = ref<number | undefined>(undefined);
const editingName = ref("");
const promptText = ref("");

const PROMPT_EXAMPLE = `示例（请按当前数据源的实际业务改写，每条用换行分隔）：
1. “渠道访问总数” 指表 t_channel_log，按 create_time 统计行数（COUNT(*)），并始终带上 is_delete = 0 过滤。
2. 涉及“去年/上月/本月”等时间口径时，按自然年/自然月计算（如去年：YEAR(create_time) = YEAR(NOW()) - 1）。
3. 时间字段优先使用 create_time；金额字段单位为元，不要换算。
4. 用户问题中出现品牌名（如“道达尔”）时无需作为过滤条件，当前库即该品牌数据。`;

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

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

const openPromptEditor = (row: DatasourceConfig) => {
  editingId.value = row.id!;
  editingName.value = row.name;
  promptText.value = row.customPrompt || "";
  drawerVisible.value = true;
};

const handleSavePrompt = async () => {
  if (editingId.value == null) return;
  saving.value = true;
  try {
    await updateDatasourcePrompt(editingId.value, promptText.value);
    ElMessage.success("已保存");
    drawerVisible.value = false;
    loadPage();
  } catch (e: any) {
    ElMessage.error(e?.message || "保存失败");
  } finally {
    saving.value = false;
  }
};

const hasPrompt = (row: DatasourceConfig) =>
  !!(row.customPrompt && row.customPrompt.trim());

onMounted(loadPage);
</script>

<template>
  <div class="page-card">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="自定义 prompt 会在 AI 生成 SQL 时，与该数据源同步得到的数据字典（表结构文档）一并注入模型，用于固化业务口径、固定过滤条件、表/字段偏好与时间口径，从而生成更精确的 SQL。"
    />

    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="数据源名称">
            <el-input v-model="queryForm.name" placeholder="数据源名称" clearable />
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="dbType" label="类型" width="100" />
      <el-table-column prop="databaseName" label="数据库" min-width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
            row.status === 1 ? "启用" : "禁用"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="自定义 prompt" width="140">
        <template #default="{ row }">
          <el-tag :type="hasPrompt(row) ? 'success' : 'info'" size="small">{{
            hasPrompt(row) ? "已设置" : "未设置"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'datasource:edit'"
            @click="openPromptEditor(row)"
            >编辑 prompt</el-button
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

    <el-drawer
      v-model="drawerVisible"
      :title="`编辑自定义 prompt - ${editingName}`"
      size="760px"
      destroy-on-close
    >
      <div class="prompt-editor">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="编写建议"
          class="mb12"
          description="用简洁的中文列出该数据源的固定规则即可；不要写表结构（表结构由数据字典提供）。规则会被注入到 SQL 生成的系统提示中，请避免与“仅生成 SELECT、不得编造表/字段”等硬性约束冲突。"
        />
        <div class="example">
          <div class="example-title">示例</div>
          <pre>{{ PROMPT_EXAMPLE }}</pre>
        </div>
        <el-input
          v-model="promptText"
          type="textarea"
          :rows="16"
          placeholder="请输入该数据源的自定义 prompt，留空表示不注入任何自定义规则"
          class="prompt-input"
        />
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSavePrompt"
            >保存</el-button
          >
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.tip {
  margin-bottom: 16px;
}

.prompt-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mb12 {
  margin-bottom: 0;
}

.example {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
  background: var(--el-fill-color-light);
}

.example-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.example pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.prompt-input :deep(textarea) {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  line-height: 1.6;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 4px;
}
</style>
