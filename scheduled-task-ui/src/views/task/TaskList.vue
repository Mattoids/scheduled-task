<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import { pageTask, deleteTask, updateTaskStatus, triggerTask } from '@/api/task'
import { listEmailConfig } from '@/api/emailConfig'
import { listRecipient, listGroup } from '@/api/emailRecipient'
import TaskForm from './TaskForm.vue'
import TaskLogDrawer from './TaskLogDrawer.vue'
import type { TaskConfig } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '任务管理' }])

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination()

const queryForm = reactive({
  taskName: '',
  taskCode: '',
})

const loading = ref(false)
const formVisible = ref(false)
const formId = ref<number | undefined>(undefined)
const logDrawerVisible = ref(false)
const logTaskId = ref<number | undefined>(undefined)

const emailConfigOptions = ref<{ label: string; value: number }[]>([])
const recipientOptions = ref<{ label: string; value: number }[]>([])
const groupOptions = ref<{ label: string; value: number }[]>([])

const loadOptions = async () => {
  const [ec, rec, grp] = await Promise.all([
    listEmailConfig({ size: 1000 }).catch(() => ({ records: [] })),
    listRecipient().catch(() => []),
    listGroup().catch(() => []),
  ])
  emailConfigOptions.value = (ec.records || []).map((item: any) => ({
    label: item.configName,
    value: item.id,
  }))
  recipientOptions.value = (rec || []).map((item: any) => ({
    label: `${item.recipientName || item.email} (${item.email})`,
    value: item.id,
  }))
  groupOptions.value = (grp || []).map((item: any) => ({
    label: item.groupName,
    value: item.id,
  }))
}

const loadPage = async () => {
  loading.value = true
  try {
    const res = await pageTask(buildQuery(queryForm))
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
  queryForm.taskName = ''
  queryForm.taskCode = ''
  reset()
  loadPage()
}

const handleCreate = () => {
  formId.value = undefined
  formVisible.value = true
}

const handleEdit = (row: TaskConfig) => {
  formId.value = row.id
  formVisible.value = true
}

const handleDelete = async (row: TaskConfig) => {
  await ElMessageBox.confirm('确认删除该任务？', '提示', { type: 'warning' })
  await deleteTask(row.id!)
  ElMessage.success('删除成功')
  loadPage()
}

const handleStatusChange = async (row: TaskConfig) => {
  const status = row.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  await updateTaskStatus(row.id!, status)
  ElMessage.success('状态更新成功')
  loadPage()
}

const handleTrigger = async (row: TaskConfig) => {
  await triggerTask(row.id!)
  ElMessage.success('任务已触发')
}

const handleLogs = (row: TaskConfig) => {
  logTaskId.value = row.id
  logDrawerVisible.value = true
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

const formatRecipientIds = (ids?: string) => {
  if (!ids) return '-'
  const idArr = ids.split(',').map((id) => Number(id.trim())).filter(Boolean)
  return idArr
    .map((id) => recipientOptions.value.find((r) => r.value === id)?.label || id)
    .join(', ')
}

const formatGroupIds = (ids?: string) => {
  if (!ids) return '-'
  const idArr = ids.split(',').map((id) => Number(id.trim())).filter(Boolean)
  return idArr
    .map((id) => groupOptions.value.find((g) => g.value === id)?.label || id)
    .join(', ')
}

onMounted(() => {
  loadOptions()
  loadPage()
})
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset"
    >
      <el-form-item label="任务名称">
        <el-input v-model="queryForm.taskName" placeholder="任务名称" clearable />
      </el-form-item>
      <el-form-item label="任务编码">
        <el-input v-model="queryForm.taskCode" placeholder="任务编码" clearable />
      </el-form-item>
    </BaseSearchForm>

    <div class="table-toolbar"
    >
      <el-button type="primary" v-permission="'task:create'" @click="handleCreate"
        >新增任务</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe
    >
      <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="taskCode" label="任务编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="triggerType" label="触发类型" width="100"
      >
        <template #default="{ row }">
          <el-tag v-if="row.triggerType === 'CRON'" type="primary">CRON</el-tag>
          <el-tag v-else type="info">单次</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggerConfig" label="触发配置" min-width="160" show-overflow-tooltip />
      <el-table-column label="邮箱配置" min-width="140"
      >
        <template #default="{ row }">
          {{ emailConfigOptions.find((e) => e.value === row.emailConfigId)?.label || row.emailConfigId }}
        </template>
      </el-table-column>
      <el-table-column label="个人收件人" min-width="160" show-overflow-tooltip
      >
        <template #default="{ row }">{{ formatRecipientIds(row.recipientIds) }}</template>
      </el-table-column>
      <el-table-column label="收件人群组" min-width="140" show-overflow-tooltip
      >
        <template #default="{ row }">{{ formatGroupIds(row.recipientGroupIds) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90"
      >
        <template #default="{ row }">
          <el-switch
            v-permission="'task:edit'"
            v-model="row.status"
            active-value="ENABLE"
            inactive-value="DISABLE"
            @change="handleStatusChange(row)"
          />
          <el-tag v-if="row.status === 'ENABLE'" type="success">启用</el-tag>
          <el-tag v-else type="danger">禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right"
      >
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'task:edit'" @click="handleEdit(row)"
            >编辑</el-button>
          <el-button link type="success" v-permission="'task:trigger'" @click="handleTrigger(row)"
            >触发</el-button>
          <el-button link type="primary" v-permission="'log:view'" @click="handleLogs(row)"
            >日志</el-button>
          <el-button link type="danger" v-permission="'task:delete'" @click="handleDelete(row)"
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

    <TaskForm
      v-model:visible="formVisible"
      :id="formId"
      :email-config-options="emailConfigOptions"
      :recipient-options="recipientOptions"
      @success="onFormSuccess"
    />

    <TaskLogDrawer v-model:visible="logDrawerVisible" :task-id="logTaskId" />
  </div>
</template>
