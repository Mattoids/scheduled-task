<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createTaskSql, getTaskSql, updateTaskSql } from '@/api/taskSql'
import type { TaskSqlConfig } from '@/types/entity'

interface Props {
  visible: boolean
  id?: number
  datasourceOptions: { label: string; value: number }[]
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
const form = ref<TaskSqlConfig>({
  sqlName: '',
  sqlCode: '',
  datasourceId: undefined as any,
  sqlContent: '',
  templateId: undefined,
  outputFormat: 'CSV',
  fileSuffix: '',
  fileNamePattern: '',
  groupName: '',
  description: '',
  status: 1,
})

const outputFormatOptions = [
  { label: 'CSV', value: 'CSV' },
  { label: 'Excel', value: 'EXCEL' },
  { label: 'Word', value: 'WORD' },
  { label: 'PPT', value: 'PPT' },
  { label: '文本', value: 'TXT' },
]

const rules = {
  sqlName: [{ required: true, message: '请输入 SQL 名称', trigger: 'blur' }],
  sqlCode: [{ required: true, message: '请输入 SQL 编码', trigger: 'blur' }],
  datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  sqlContent: [{ required: true, message: '请输入 SQL 内容', trigger: 'blur' }],
}

const isEdit = computed(() => !!props.id)
const title = computed(() => (isEdit.value ? '编辑 SQL' : '新增 SQL'))

const resetForm = () => {
  form.value = {
    sqlName: '',
    sqlCode: '',
    datasourceId: undefined as any,
    sqlContent: '',
    templateId: undefined,
    outputFormat: 'CSV',
    fileSuffix: '',
    fileNamePattern: '',
    groupName: '',
    description: '',
    status: 1,
  }
}

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res = await getTaskSql(props.id)
    form.value = res
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

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value) {
      await updateTaskSql(props.id!, form.value)
    } else {
      await createTaskSql(form.value)
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
  <el-dialog v-model="dialogVisible" :title="title" width="780px" @close="handleClose">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
      v-loading="loading"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="SQL 名称" prop="sqlName">
            <el-input v-model="form.sqlName" placeholder="SQL 名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="SQL 编码" prop="sqlCode">
            <el-input v-model="form.sqlCode" placeholder="SQL 编码" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="数据源" prop="datasourceId">
        <el-select v-model="form.datasourceId" placeholder="请选择数据源" style="width: 100%">
          <el-option
            v-for="item in datasourceOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="SQL 内容" prop="sqlContent">
        <el-input
          v-model="form.sqlContent"
          type="textarea"
          :rows="5"
          placeholder="请输入要执行的 SQL"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="报表模板">
            <el-select v-model="form.templateId" placeholder="请选择模板（可选）" clearable style="width: 100%">
              <el-option
                v-for="item in templateOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="输出格式">
            <el-select v-model="form.outputFormat" placeholder="输出格式" style="width: 100%">
              <el-option
                v-for="item in outputFormatOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="文件后缀">
            <el-input v-model="form.fileSuffix" placeholder="如 csv、xlsx（可选）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="文件名格式">
            <el-input v-model="form.fileNamePattern" placeholder="report_{yyyyMMddHHmmss}（可选）" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="分组名称">
        <el-input v-model="form.groupName" placeholder="如 门店报表、财务日报（可选，用于任务选择时分组展示）" />
      </el-form-item>

      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="描述（可选）" />
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :label="1">启用</el-radio>
          <el-radio :label="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
