<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import {
  pageStorageConfig,
  deleteStorageConfig,
  getStorageConfig,
  testStorageConfig,
} from '@/api/storageConfig'
import StorageConfigForm from './StorageConfigForm.vue'
import type { StorageConfig } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '存储配置' }])

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination()
const loading = ref(false)
const queryForm = reactive({ configName: '', storageType: '' })
const formVisible = ref(false)
const formId = ref<number | undefined>(undefined)

const typeOptions = [
  { label: '本地存储', value: 'LOCAL' },
  { label: '阿里云 OSS', value: 'OSS' },
  { label: 'S3 / MinIO', value: 'S3' },
  { label: 'WebDAV', value: 'WEBDAV' },
]

const formatType = (value?: string) => {
  return typeOptions.find((item) => item.value === value)?.label || value || '-'
}

const loadPage = async () => {
  loading.value = true
  try {
    const res = await pageStorageConfig(buildQuery(queryForm))
    setPageResult(res)
  } finally {
    loading.value = false
  }
}
const handleSearch = () => {
  current.value = 1
  loadPage()
}
const handleReset = () => {
  queryForm.configName = ''
  queryForm.storageType = ''
  reset()
  loadPage()
}
const handleCreate = () => {
  formId.value = undefined
  formVisible.value = true
}
const handleEdit = (row: StorageConfig) => {
  formId.value = row.id
  formVisible.value = true
}
const handleDelete = async (row: StorageConfig) => {
  await ElMessageBox.confirm('确认删除该存储配置？', '提示', {
    type: 'warning',
  })
  await deleteStorageConfig(row.id!)
  ElMessage.success('删除成功')
  loadPage()
}
const handleTest = async (row: StorageConfig) => {
  loading.value = true
  try {
    const message = await testStorageConfig(row.id!)
    ElMessage.success(message || '测试成功')
  } catch (e: any) {
    ElMessage.error(e.message || '测试失败')
  } finally {
    loading.value = false
  }
}
const onFormSuccess = () => {
  formVisible.value = false
  loadPage()
}
const handlePageChange = (c: number, s: number) => {
  current.value = c
  size.value = s
  loadPage()
}
onMounted(loadPage)
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
          <el-form-item label="存储类型">
            <el-select
              v-model="queryForm.storageType"
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
        v-permission="'storageConfig:create'"
        @click="handleCreate"
        >新增配置</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="configName" label="配置名称" min-width="160" />
      <el-table-column label="存储类型" min-width="160">
        <template #default="{ row }">{{ formatType(row.storageType) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
            row.status === 1 ? '启用' : '禁用'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isDefault" label="默认" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isDefault === 1 ? 'success' : 'info'">{{
            row.isDefault === 1 ? '是' : '否'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'storageConfig:edit'"
            @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            type="primary"
            v-permission="'storageConfig:view'"
            @click="handleTest(row)"
            >测试</el-button
          >
          <el-button
            link
            type="danger"
            v-permission="'storageConfig:delete'"
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

    <StorageConfigForm
      v-model:visible="formVisible"
      :id="formId"
      @success="onFormSuccess"
    />
  </div>
</template>
