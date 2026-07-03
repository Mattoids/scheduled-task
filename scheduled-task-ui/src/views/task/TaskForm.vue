<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, getTask, updateTask } from '@/api/task'
import { listTaskSql } from '@/api/taskSql'
import { listGroup } from '@/api/emailRecipient'
import type { TaskConfig, TaskConfigRequest, TaskSqlConfig } from '@/types/entity'

interface Props {
  visible: boolean
  id?: number
  emailConfigOptions: { label: string; value: number }[]
  recipientOptions: { label: string; value: number }[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const loading = ref(false)
const formRef = ref()
const form = ref<TaskConfig>({
  taskName: '',
  taskCode: '',
  triggerType: 'CRON',
  triggerConfig: '',
  emailConfigId: undefined as any,
  recipientIds: '',
  recipientGroupIds: '',
  status: 'ENABLE',
  emailSubject: '定时报表',
  emailBody: '请查收附件报表。',
})

const sqlOptions = ref<TaskSqlConfig[]>([])
const groupOptions = ref<{ label: string; value: number }[]>([])
const selectedRecipients = ref<number[]>([])
const selectedGroups = ref<number[]>([])
const selectedSqlIds = ref<number[]>([])

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  triggerType: [{ required: true, message: '请选择触发类型', trigger: 'change' }],
  triggerConfig: [{ required: true, message: '请输入触发配置', trigger: 'blur' }],
  emailConfigId: [{ required: true, message: '请选择邮箱配置', trigger: 'change' }],
}

const isEdit = computed(() => !!props.id)
const title = computed(() => (isEdit.value ? '编辑任务' : '新增任务'))

const selectedSqlList = computed(() => {
  return selectedSqlIds.value
    .map((id) => sqlOptions.value.find((sql) => sql.id === id))
    .filter((sql): sql is TaskSqlConfig => !!sql)
})

const loadOptions = async () => {
  const [sqlRes, groupRes] = await Promise.all([
    listTaskSql().catch(() => []),
    listGroup().catch(() => []),
  ])
  sqlOptions.value = sqlRes || []
  groupOptions.value = (groupRes || []).map((item: any) => ({
    label: item.groupName,
    value: item.id,
  }))
}

const resetForm = () => {
  form.value = {
    taskName: '',
    taskCode: '',
    triggerType: 'CRON',
    triggerConfig: '',
    emailConfigId: undefined as any,
    recipientIds: '',
    recipientGroupIds: '',
    status: 'ENABLE',
    emailSubject: '定时报表',
    emailBody: '请查收附件报表。',
  }
  selectedRecipients.value = []
  selectedGroups.value = []
  selectedSqlIds.value = []
}

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res: TaskConfigRequest = await getTask(props.id)
    form.value = res.task || {
      taskName: '',
      taskCode: '',
      triggerType: 'CRON',
      triggerConfig: '',
      emailConfigId: undefined as any,
      recipientIds: '',
      recipientGroupIds: '',
      status: 'ENABLE',
      emailSubject: '定时报表',
      emailBody: '请查收附件报表。',
    }
    selectedRecipients.value = form.value.recipientIds
      ? form.value.recipientIds.split(',').map((id) => Number(id.trim())).filter(Boolean)
      : []
    selectedGroups.value = form.value.recipientGroupIds
      ? form.value.recipientGroupIds.split(',').map((id) => Number(id.trim())).filter(Boolean)
      : []
    // 收件人与收件人群组互斥，若都存在则优先保留收件人
    if (selectedRecipients.value.length > 0 && selectedGroups.value.length > 0) {
      selectedGroups.value = []
    }
    selectedSqlIds.value = res.sqlIds || []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetForm()
      if (props.id) {
        loadDetail()
      }
    }
  }
)

watch(
  () => selectedRecipients.value,
  (val) => {
    form.value.recipientIds = val.join(',')
    if (val.length > 0) {
      selectedGroups.value = []
    }
  }
)

watch(
  () => selectedGroups.value,
  (val) => {
    form.value.recipientGroupIds = val.join(',')
    if (val.length > 0) {
      selectedRecipients.value = []
    }
  }
)

const moveSqlUp = (index: number) => {
  if (index <= 0) return
  const arr = [...selectedSqlIds.value]
  const temp = arr[index]
  arr[index] = arr[index - 1]
  arr[index - 1] = temp
  selectedSqlIds.value = arr
}

const moveSqlDown = (index: number) => {
  if (index >= selectedSqlIds.value.length - 1) return
  const arr = [...selectedSqlIds.value]
  const temp = arr[index]
  arr[index] = arr[index + 1]
  arr[index + 1] = temp
  selectedSqlIds.value = arr
}

const removeSql = (index: number) => {
  const arr = [...selectedSqlIds.value]
  arr.splice(index, 1)
  selectedSqlIds.value = arr
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (selectedSqlIds.value.length === 0) {
    ElMessage.warning('请选择至少一条 SQL')
    return
  }

  loading.value = true
  try {
    const request: TaskConfigRequest = {
      task: form.value,
      sqlIds: selectedSqlIds.value,
    }
    if (isEdit.value) {
      await updateTask(props.id!, request)
    } else {
      await createTask(request)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    emit('success')
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  emit('update:visible', false)
}

onMounted(() => {
  loadOptions()
})
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="820px" @close="handleClose">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      v-loading="loading"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务名称" prop="taskName">
            <el-input v-model="form.taskName" placeholder="任务名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="任务编码" prop="taskCode">
            <el-input v-model="form.taskCode" placeholder="任务编码" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="触发类型" prop="triggerType">
            <el-radio-group v-model="form.triggerType">
              <el-radio label="CRON">CRON</el-radio>
              <el-radio label="ONCE">单次</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="触发配置" prop="triggerConfig">
            <el-input v-model="form.triggerConfig" :placeholder="form.triggerType === 'CRON' ? '0 0 9 * * ?' : '2026-01-01 09:00:00'" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="选择 SQL" required>
        <el-select
          v-model="selectedSqlIds"
          multiple
          collapse-tags
          placeholder="请选择要执行的 SQL"
          style="width: 100%"
        >
          <el-option
            v-for="item in sqlOptions"
            :key="item.id"
            :label="item.sqlName"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="selectedSqlList.length > 0" label="执行顺序">
        <el-table :data="selectedSqlList" border size="small" style="width: 100%">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="sqlName" label="SQL 名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="sqlCode" label="SQL 编码" min-width="120" show-overflow-tooltip />
          <el-table-column label="模板" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.templateId ? '有' : '无' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button link type="primary" :disabled="$index === 0" @click="moveSqlUp($index)"
                >上移</el-button>
              <el-button link type="primary" :disabled="$index === selectedSqlList.length - 1" @click="moveSqlDown($index)"
                >下移</el-button>
              <el-button link type="danger" @click="removeSql($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="邮箱配置" prop="emailConfigId">
            <el-select v-model="form.emailConfigId" placeholder="请选择邮箱配置" style="width: 100%">
              <el-option
                v-for="item in emailConfigOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="收件人">
        <el-select
          v-model="selectedRecipients"
          multiple
          collapse-tags
          placeholder="请选择收件人"
          style="width: 100%"
        >
          <el-option
            v-for="item in recipientOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="收件人群组">
        <el-select
          v-model="selectedGroups"
          multiple
          collapse-tags
          placeholder="请选择收件人群组"
          style="width: 100%"
        >
          <el-option
            v-for="item in groupOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <!-- 文件名格式已移除，改在 SQL 管理中配置 -->

      <el-form-item label="邮件主题">
        <el-input v-model="form.emailSubject" placeholder="邮件主题" />
      </el-form-item>

      <el-form-item label="邮件正文">
        <el-input v-model="form.emailBody" type="textarea" :rows="3" placeholder="邮件正文" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
