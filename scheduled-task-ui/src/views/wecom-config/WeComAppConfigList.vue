<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import {
  pageWeComAppConfig,
  deleteWeComAppConfig,
  getWeComAppConfig,
  testWeComAppConfig,
} from '@/api/weComAppConfig'
import WeComAppConfigForm from './WeComAppConfigForm.vue'
import type { WeComAppConfig } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '企业微信应用配置' }])

const { current, size, total, records, buildQuery, setPageResult, reset } = usePagination()
const loading = ref(false)
const queryForm = reactive({ configName: '' })
const formVisible = ref(false)
const formId = ref<number | undefined>(undefined)

const loadPage = async () => {
  loading.value = true
  try {
    const res = await pageWeComAppConfig(buildQuery(queryForm))
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
  reset()
  loadPage()
}
const handleCreate = () => {
  formId.value = undefined
  formVisible.value = true
}
const handleEdit = (row: WeComAppConfig) => {
  formId.value = row.id
  formVisible.value = true
}
const handleDelete = async (row: WeComAppConfig) => {
  await ElMessageBox.confirm('确认删除该企业微信应用配置？', '提示', { type: 'warning' })
  await deleteWeComAppConfig(row.id!)
  ElMessage.success('删除成功')
  loadPage()
}
const handleTest = async (row: WeComAppConfig) => {
  loading.value = true
  try {
    const detail = await getWeComAppConfig(row.id!)
    const res = await testWeComAppConfig(detail)
    if (res.success) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
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
    <BaseSearchForm @search="handleSearch" @reset="handleReset"
      >
      <el-form-item label="配置名称">
        <el-input v-model="queryForm.configName" placeholder="配置名称" clearable />
      </el-form-item>
    </BaseSearchForm>

    <div class="table-toolbar"
      >
      <el-button type="primary" v-permission="'wecomApp:create'" @click="handleCreate">新增应用配置</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe
      >
      <el-table-column prop="configName" label="配置名称" min-width="140" />
      <el-table-column prop="corpId" label="企业 ID" min-width="160" show-overflow-tooltip />
      <el-table-column prop="agentId" label="应用 ID" width="90" />
      <el-table-column prop="token" label="Token" min-width="120" show-overflow-tooltip />
      <el-table-column prop="aesKey" label="AES Key" min-width="160" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90"
        >
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'"
            >{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right"
        >
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'wecomApp:edit'" @click="handleEdit(row)"
            >编辑</el-button>
          <el-button link type="primary" v-permission="'wecomApp:view'" @click="handleTest(row)"
            >测试</el-button>
          <el-button link type="danger" v-permission="'wecomApp:delete'" @click="handleDelete(row)"
            >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />

    <WeComAppConfigForm v-model:visible="formVisible" :id="formId" @success="onFormSuccess" />
  </div>
</template>
