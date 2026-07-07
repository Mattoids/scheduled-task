<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import {
  pageNotificationConfig,
  deleteNotificationConfig,
  getNotificationConfig,
  testNotificationConfig,
} from "@/api/notificationConfig";
import NotificationConfigForm from "./NotificationConfigForm.vue";
import type { NotificationConfig } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "通知配置" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({ configName: "", configType: "" });
const formVisible = ref(false);
const formId = ref<number | undefined>(undefined);

const typeOptions = [
  { label: "邮箱", value: "EMAIL" },
  { label: "企业微信应用", value: "WECOM_APP" },
  { label: "企业微信群机器人", value: "WECOM_BOT" },
  { label: "企业微信智能机器人", value: "WECOM_INTELLIGENT_BOT" },
  { label: "钉钉群机器人", value: "DINGTALK" },
  { label: "飞书群机器人", value: "FEISHU" },
  { label: "Slack", value: "SLACK" },
  { label: "Webhook", value: "WEBHOOK" },
];

const formatType = (value?: string) => {
  return typeOptions.find((item) => item.value === value)?.label || value || "-";
};

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageNotificationConfig(buildQuery(queryForm));
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
  queryForm.configType = "";
  reset();
  loadPage();
};
const handleCreate = () => {
  formId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (row: NotificationConfig) => {
  formId.value = row.id;
  formVisible.value = true;
};
const handleDelete = async (row: NotificationConfig) => {
  await ElMessageBox.confirm("确认删除该通知配置？", "提示", {
    type: "warning",
  });
  await deleteNotificationConfig(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};
const handleTest = async (row: NotificationConfig) => {
  loading.value = true;
  try {
    const detail = await getNotificationConfig(row.id!);
    const res = await testNotificationConfig(detail);
    if (res.success) {
      ElMessage.success("连接成功");
    } else {
      ElMessage.error(res.message || "连接失败");
    }
  } finally {
    loading.value = false;
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
          <el-form-item label="配置名称">
            <el-input
              v-model="queryForm.configName"
              placeholder="配置名称"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="配置类型">
            <el-select
              v-model="queryForm.configType"
              placeholder="全部类型"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="item in typeOptions"
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
        v-permission="'notificationConfig:create'"
        @click="handleCreate"
        >新增配置</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="configName" label="配置名称" min-width="160" />
      <el-table-column prop="configCode" label="配置编码" min-width="140" />
      <el-table-column label="配置类型" min-width="160">
        <template #default="{ row }">{{ formatType(row.configType) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
            row.status === 1 ? "启用" : "禁用"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'notificationConfig:edit'"
            @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            type="primary"
            v-permission="'notificationConfig:view'"
            @click="handleTest(row)"
            >测试</el-button
          >
          <el-button
            link
            type="danger"
            v-permission="'notificationConfig:delete'"
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

    <NotificationConfigForm
      v-model:visible="formVisible"
      :id="formId"
      @success="onFormSuccess"
    />
  </div>
</template>
