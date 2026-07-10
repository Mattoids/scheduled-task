<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import {
  pageAiConfig,
  deleteAiConfig,
  getAiConfig,
  testAiConfig,
  updateAiConfigDefault,
} from "@/api/aiConfig";
import AiConfigForm from "./AiConfigForm.vue";
import type { AiConfig } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "AI 配置管理" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({ configName: "", provider: "" });
const formVisible = ref(false);
const formId = ref<number | undefined>(undefined);

const providerOptions = [
  { label: "OpenAI / 兼容", value: "OPENAI" },
  { label: "Anthropic", value: "ANTHROPIC" },
  { label: "Azure OpenAI", value: "AZURE_OPENAI" },
  { label: "Ollama", value: "OLLAMA" },
  { label: "自定义", value: "CUSTOM" },
];

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageAiConfig(buildQuery(queryForm));
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
  queryForm.configName = "";
  queryForm.provider = "";
  reset();
  loadPage();
};

const handleCreate = () => {
  formId.value = undefined;
  formVisible.value = true;
};

const handleEdit = (row: AiConfig) => {
  formId.value = row.id;
  formVisible.value = true;
};

const handleDelete = async (row: AiConfig) => {
  await ElMessageBox.confirm("确认删除该 AI 配置？", "提示", {
    type: "warning",
  });
  await deleteAiConfig(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};

const handleTest = async (row: AiConfig) => {
  try {
    const res = await testAiConfig(row.id!);
    ElMessageBox.alert(res || "测试成功，未返回内容", "AI 配置测试成功", {
      type: "success",
    });
  } catch (e: any) {
    ElMessage.error(e?.message || "测试失败");
  }
};

const handleDefaultChange = async (row: AiConfig) => {
  const isDefault = row.isDefault === 1 ? 0 : 1;
  await updateAiConfigDefault(row.id!, isDefault);
  ElMessage.success("默认状态更新成功");
  loadPage();
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
          <el-form-item label="配置名称">
            <el-input
              v-model="queryForm.configName"
              placeholder="配置名称"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="AI 厂商">
            <el-select v-model="queryForm.provider" placeholder="全部" clearable>
              <el-option
                v-for="item in providerOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button
        type="primary"
        v-permission="'system:user'"
        @click="handleCreate"
        >新增 AI 配置</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="configName" label="配置名称" min-width="160" />
      <el-table-column prop="provider" label="厂商" width="140">
        <template #default="{ row }">
          {{
            providerOptions.find((item) => item.value === row.provider)
              ?.label || row.provider
          }}
        </template>
      </el-table-column>
      <el-table-column
        prop="model"
        label="模型"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="baseUrl"
        label="Base URL"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column prop="isDefault" label="默认" width="120" align="center">
        <template #default="{ row }">
          <el-switch
            v-permission="'system:user'"
            :model-value="row.isDefault"
            :active-value="1"
            :inactive-value="0"
            @change="handleDefaultChange(row)"
          />
          <el-tag :type="row.isDefault === 1 ? 'success' : 'info'">{{
            row.isDefault === 1 ? "是" : "否"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
            row.status === 1 ? "启用" : "禁用"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'system:user'"
            @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-permission="'system:user'"
            @click="handleTest(row)"
            >测试</el-button
          >
          <el-button
            link
            type="danger"
            v-permission="'system:user'"
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

    <AiConfigForm
      v-model:visible="formVisible"
      :id="formId"
      @success="onFormSuccess"
    />
  </div>
</template>
