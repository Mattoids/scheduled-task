<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, getTask, updateTask } from '@/api/task'
import type { TaskConfig } from '@/types/entity'

interface Props {
  visible: boolean
  id?: number
  datasourceOptions: { label: string; value: number }[]
  emailConfigOptions: { label: string; value: number }[]
  recipientOptions: { label: string; value: number }[]
  templateOptions: { label: string; value: number }[]
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
  datasourceId: undefined as any,
  sqlContent: '',
  emailConfigId: undefined as any,
  recipientIds: '',
  templateId: undefined,
  status: 'ENABLE',
  fileNamePattern: 'report_{yyyyMMddHHmmss}',
  emailSubject: '定时报表',
  emailBody: '请查收附件报表。',
})

const selectedRecipients = ref<number[]>([])

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  triggerType: [{ required: true, message: '请选择触发类型', trigger: 'change' }],
  triggerConfig: [{ required: true, message: '请输入触发配置', trigger: 'blur' }],
  datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  sqlContent: [{ required: true, message: '请输入 SQL', trigger: 'blur' }],
  emailConfigId: [{ required: true, message: '请选择邮箱配置', trigger: 'change' }],
}

const isEdit = computed(() => !!props.id)
const title = computed(() => (isEdit.value ? '编辑任务' : '新增任务'))

const resetForm = () => {
  form.value = {
    taskName: '',
    taskCode: '',
    triggerType: 'CRON',
    triggerConfig: '',
    datasourceId: undefined as any,
    sqlContent: '',
    emailConfigId: undefined as any,
    recipientIds: '',
    templateId: undefined,
    status: 'ENABLE',
    fileNamePattern: 'report_{yyyyMMddHHmmss}',
    emailSubject: '定时报表',
    emailBody: '请查收附件报表。',
  }
  selectedRecipients.value = []
}

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res = await getTask(props.id)
    form.value = res
    selectedRecipients.value = res.recipientIds
      ? res.recipientIds.split(',').map((id) => Number(id.trim()))
      : []
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
  }
)

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value) {
      await updateTask(props.id!, form.value)
    } else {
      await createTask(form.value)
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
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="760px" @close="handleClose"
    >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      v-loading="loading"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务名称" prop="taskName"
            >
            <el-input v-model="form.taskName" placeholder="任务名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="任务编码" prop="taskCode"
            >
            <el-input v-model="form.taskCode" placeholder="任务编码" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="触发类型" prop="triggerType"
            >
            <el-radio-group v-model="form.triggerType"
              >
                <el-radio label="CRON">CRON</el-radio>
                <el-radio label="ONCE">单次</el-radio>
              </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="触发配置" prop="triggerConfig"
            >
            <el-input v-model="form.triggerConfig" :placeholder="form.triggerType === 'CRON' ? '0 0 9 * * ?' : '2026-01-01 09:00:00'" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="数据源" prop="datasourceId"
        >
        <el-select v-model="form.datasourceId" placeholder="请选择数据源" style="width: 100%"
          >
          <el-option
            v-for="item in datasourceOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="SQL 内容" prop="sqlContent"
        >
        <el-input
          v-model="form.sqlContent"
          type="textarea"
          :rows="5"
          placeholder="请输入要执行的 SQL"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="邮箱配置" prop="emailConfigId"
            >
            <el-select v-model="form.emailConfigId" placeholder="请选择邮箱配置" style="width: 100%"
              >
              <el-option
                v-for="item in emailConfigOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="报表模板"
            >
            <el-select v-model="form.templateId" placeholder="请选择模板（可选）" clearable style="width: 100%"
              >
              <el-option
                v-for="item in templateOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="收件人"
        >
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

      <el-form-item label="文件名格式"
        >
        <el-input v-model="form.fileNamePattern" placeholder="report_{yyyyMMddHHmmss}" />
      </el-form-item>

      <el-form-item label="邮件主题"
        >
        <el-input v-model="form.emailSubject" placeholder="邮件主题" />
      </el-form-item>

      <el-form-item label="邮件正文"
        >
        <el-input v-model="form.emailBody" type="textarea" :rows="3" placeholder="邮件正文" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
