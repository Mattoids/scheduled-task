<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import {
  pageDatasource,
  deleteDatasource,
  testDatasource,
} from "@/api/datasource";
import DatasourceForm from "./DatasourceForm.vue";
import type { DatasourceConfig } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "数据源管理" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({ name: "" });
const formVisible = ref(false);
const formId = ref<number | undefined>(undefined);

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
      <el-table-column label="操作" width="220" fixed="right">
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
  </div>
</template>
