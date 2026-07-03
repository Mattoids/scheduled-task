<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import {
  pageWeComBotConfig,
  deleteWeComBotConfig,
  getWeComBotConfig,
  testWeComBotConfig,
} from '@/api/weComBotConfig'
import WeComBotConfigForm from './WeComBotConfigForm.vue'
import type { WeComBotConfig } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '企业微信群机器人配置' }])

const { current, size, total, records, buildQuery, setPageResult, reset } = usePagination()
const loading = ref(false)
const queryForm = reactive({ configName: '' })
const formVisible = ref(false)
const formId = ref<number | undefined>(undefined)

const loadPage = async () => {
  loading.value = true
  try {
    const res = await pageWeComBotConfig(buildQuery(queryForm))
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
const handleEdit = (row: WeComBotConfig) => {
  formId.value = row.id
  formVisible.value = true
}
const handleDelete = async (row: WeComBotConfig) => {
  await ElMessageBox.confirm('确认删除该企业微信群机器人配置？', '提示', { type: 'warning' })
  await deleteWeComBotConfig(row.id!)
  ElMessage.success('删除成功')
  loadPage()
}
const handleTest = async (row: WeComBotConfig) => {
  loading.value = true
  try {
    const detail = await getWeComBotConfig(row.id!)
    const res = await testWeComBotConfig(detail)
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
      <el-button type="primary" v-permission="'wecomBot:create'" @click="handleCreate">新增机器人配置</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe
      >
      <el-table-column prop="configName" label="配置名称" min-width="160" />
      <el-table-column prop="webhookKey" label="Webhook Key" min-width="240" show-overflow-tooltip />
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
          <el-button link type="primary" v-permission="'wecomBot:edit'" @click="handleEdit(row)"
            >编辑</el-button>
          <el-button link type="primary" v-permission="'wecomBot:view'" @click="handleTest(row)"
            >测试</el-button>
          <el-button link type="danger" v-permission="'wecomBot:delete'" @click="handleDelete(row)"
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

    <WeComBotConfigForm v-model:visible="formVisible" :id="formId" @success="onFormSuccess" />
  </div>
</template>
