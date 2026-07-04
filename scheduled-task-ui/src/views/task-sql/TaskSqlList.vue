<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import { pageTaskSql, deleteTaskSql } from "@/api/taskSql";
import {
  pageTaskSqlGroup,
  deleteTaskSqlGroup,
  listTaskSqlGroup,
} from "@/api/taskSqlGroup";
import { listDatasource } from "@/api/datasource";
import { listTemplate } from "@/api/template";
import TaskSqlForm from "./TaskSqlForm.vue";
import TaskSqlGroupForm from "./TaskSqlGroupForm.vue";
import type { TaskSqlConfig, TaskSqlGroup } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "SQL 管理" }]);

const activeTab = ref("sql");

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();

const queryForm = reactive({
  sqlName: "",
  sqlCode: "",
  groupId: undefined as number | undefined,
});

const groupQuery = reactive({
  groupName: "",
});

const groupPagination = usePagination();

const loading = ref(false);
const groupLoading = ref(false);
const formVisible = ref(false);
const formId = ref<number | undefined>(undefined);
const groupFormVisible = ref(false);
const groupFormId = ref<number | undefined>(undefined);

const datasourceOptions = ref<{ label: string; value: number }[]>([]);
const templateOptions = ref<{ label: string; value: number }[]>([]);
const groupOptions = ref<
  { label: string; value: number; fileNamePattern?: string }[]
>([]);

const loadOptions = async () => {
  const [ds, tpl, grp] = await Promise.all([
    listDatasource({ size: 1000 }).catch(() => ({ records: [] })),
    listTemplate({ size: 1000 }).catch(() => ({ records: [] })),
    listTaskSqlGroup().catch(() => []),
  ]);
  datasourceOptions.value = (ds.records || []).map((item: any) => ({
    label: item.name,
    value: item.id,
  }));
  templateOptions.value = (tpl.records || []).map((item: any) => ({
    label: `${item.templateName} (${item.templateType})`,
    value: item.id,
  }));
  groupOptions.value = (grp || []).map((item: TaskSqlGroup) => ({
    label: item.groupName,
    value: item.id!,
    fileNamePattern: item.fileNamePattern,
  }));
};

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageTaskSql(buildQuery(queryForm));
    setPageResult(res);
  } finally {
    loading.value = false;
  }
};

const loadGroupPage = async () => {
  groupLoading.value = true;
  try {
    const res = await pageTaskSqlGroup(groupPagination.buildQuery(groupQuery));
    groupPagination.setPageResult(res);
  } finally {
    groupLoading.value = false;
  }
};

const handleSearch = () => {
  current.value = 1;
  loadPage();
};

const handleReset = () => {
  queryForm.sqlName = "";
  queryForm.sqlCode = "";
  queryForm.groupId = undefined;
  reset();
  loadPage();
};

const handleGroupSearch = () => {
  groupPagination.current.value = 1;
  loadGroupPage();
};

const handleGroupReset = () => {
  groupQuery.groupName = "";
  groupPagination.reset();
  loadGroupPage();
};

const handleCreate = () => {
  formId.value = undefined;
  formVisible.value = true;
};

const handleEdit = (row: TaskSqlConfig) => {
  formId.value = row.id;
  formVisible.value = true;
};

const handleDelete = async (row: TaskSqlConfig) => {
  await ElMessageBox.confirm("确认删除该 SQL 配置？", "提示", {
    type: "warning",
  });
  await deleteTaskSql(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};

const handleCreateGroup = () => {
  groupFormId.value = undefined;
  groupFormVisible.value = true;
};

const handleEditGroup = (row: TaskSqlGroup) => {
  groupFormId.value = row.id;
  groupFormVisible.value = true;
};

const handleDeleteGroup = async (row: TaskSqlGroup) => {
  await ElMessageBox.confirm("确认删除该 SQL 分组？", "提示", {
    type: "warning",
  });
  await deleteTaskSqlGroup(row.id!);
  ElMessage.success("删除成功");
  loadGroupPage();
  loadOptions();
  loadPage();
};

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

const handleGroupPageChange = (c: number, s: number) => {
  groupPagination.current.value = c;
  groupPagination.size.value = s;
  loadGroupPage();
};

const onFormSuccess = () => {
  formVisible.value = false;
  loadPage();
};

const onGroupFormSuccess = () => {
  groupFormVisible.value = false;
  loadGroupPage();
  loadOptions();
};

onMounted(() => {
  loadOptions();
  loadPage();
  loadGroupPage();
});
</script>

<template>
  <div class="page-card">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="SQL 配置" name="sql">
        <BaseSearchForm @search="handleSearch" @reset="handleReset">
          <el-row>
            <el-col :span="6">
              <el-form-item label="SQL 名称">
                <el-input
                    v-model="queryForm.sqlName"
                    placeholder="SQL 名称"
                    clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="SQL 编码">
                <el-input
                    v-model="queryForm.sqlCode"
                    placeholder="SQL 编码"
                    clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="分组">
                <el-select
                    v-model="queryForm.groupId"
                    placeholder="全部分组"
                    clearable
                >
                  <el-option
                      v-for="item in groupOptions"
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
            v-permission="'task:create'"
            @click="handleCreate"
            >新增 SQL</el-button
          >
        </div>

        <el-table v-loading="loading" :data="records" border stripe>
          <el-table-column
            prop="sqlName"
            label="SQL 名称"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="sqlCode"
            label="SQL 编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="groupName"
            label="分组"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column label="数据源" min-width="140">
            <template #default="{ row }">
              {{
                datasourceOptions.find((d) => d.value === row.datasourceId)
                  ?.label || row.datasourceId
              }}
            </template>
          </el-table-column>
          <el-table-column label="模板" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{
                templateOptions.find((t) => t.value === row.templateId)
                  ?.label || "-"
              }}
            </template>
          </el-table-column>
          <el-table-column prop="outputFormat" label="输出格式" width="100" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success">启用</el-tag>
              <el-tag v-else type="danger">禁用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
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
      </el-tab-pane>

      <el-tab-pane label="SQL 分组" name="group">
        <BaseSearchForm @search="handleGroupSearch" @reset="handleGroupReset">
          <el-row>
            <el-col :span="6">
              <el-form-item label="分组名称">
                <el-input
                  v-model="groupQuery.groupName"
                  placeholder="分组名称"
                  clearable
                />
              </el-form-item>
            </el-col>
          </el-row>
        </BaseSearchForm>

        <div class="table-toolbar">
          <el-button
            type="primary"
            v-permission="'taskSqlGroup:create'"
            @click="handleCreateGroup"
            >新增分组</el-button
          >
        </div>

        <el-table
          v-loading="groupLoading"
          :data="groupPagination.records.value"
          border
          stripe
        >
          <el-table-column
            prop="groupName"
            label="分组名称"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="groupCode"
            label="分组编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="fileNamePattern"
            label="文件名格式"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="description"
            label="描述"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success">启用</el-tag>
              <el-tag v-else type="danger">禁用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                v-permission="'taskSqlGroup:edit'"
                @click="handleEditGroup(row)"
                >编辑</el-button
              >
              <el-button
                link
                type="danger"
                v-permission="'taskSqlGroup:delete'"
                @click="handleDeleteGroup(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>

        <BasePagination
          :total="groupPagination.total.value"
          :current="groupPagination.current.value"
          :size="groupPagination.size.value"
          @change="handleGroupPageChange"
        />
      </el-tab-pane>
    </el-tabs>

    <TaskSqlForm
      v-model:visible="formVisible"
      :id="formId"
      :datasource-options="datasourceOptions"
      :template-options="templateOptions"
      :group-options="groupOptions"
      @success="onFormSuccess"
    />

    <TaskSqlGroupForm
      v-model:visible="groupFormVisible"
      :id="groupFormId"
      @success="onGroupFormSuccess"
    />
  </div>
</template>
