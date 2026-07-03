<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createEmailConfig, getEmailConfig, updateEmailConfig, testEmailConfig } from '@/api/emailConfig'
import type { EmailConfig } from '@/types/entity'

interface Props {
  visible: boolean
  id?: number
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
const form = ref<EmailConfig>({
  configName: '',
  smtpHost: '',
  smtpPort: 587,
  username: '',
  password: '',
  fromAddress: '',
  fromName: '',
  auth: 1,
  starttls: 1,
  ssl: 0,
  status: 1,
})

const rules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  smtpHost: [{ required: true, message: '请输入 SMTP 主机', trigger: 'blur' }],
  smtpPort: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  fromAddress: [{ required: true, message: '请输入发件地址', trigger: 'blur' }],
}

const isEdit = computed(() => !!props.id)
const title = computed(() => (isEdit.value ? '编辑邮箱配置' : '新增邮箱配置'))

const resetForm = () => {
  form.value = {
    configName: '',
    smtpHost: '',
    smtpPort: 587,
    username: '',
    password: '',
    fromAddress: '',
    fromName: '',
    auth: 1,
    starttls: 1,
    ssl: 0,
    status: 1,
  }
}

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res = await getEmailConfig(props.id)
    form.value = { ...res, password: '' }
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetForm()
      if (props.id) loadDetail()
    }
  }
)

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = { ...form.value }
    if (isEdit.value && !data.password) {
      delete data.password
    }
    if (isEdit.value) {
      await updateEmailConfig(props.id!, data)
    } else {
      await createEmailConfig(data)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    emit('success')
  } finally {
    loading.value = false
  }
}

const handleTest = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = { ...form.value }
    if (isEdit.value && props.id && !data.password) {
      const detail = await getEmailConfig(props.id)
      data.password = detail.password
    }
    const res = await testEmailConfig(data)
    if (res.success) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="700px"
    >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" v-loading="loading"
      >
      <el-form-item label="配置名称" prop="configName"
        >
        <el-input v-model="form.configName" placeholder="配置名称" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="16">
          <el-form-item label="SMTP 主机" prop="smtpHost"
            >
            <el-input v-model="form.smtpHost" placeholder="如 smtp.example.com" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="端口" prop="smtpPort"
            >
            <el-input-number v-model="form.smtpPort" :min="1" :max="65535" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="用户名" prop="username"
            >
            <el-input v-model="form.username" placeholder="用户名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="密码"
            >
            <el-input v-model="form.password" type="password" placeholder="留空表示不修改" show-password />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="发件地址" prop="fromAddress"
            >
            <el-input v-model="form.fromAddress" placeholder="如 noreply@example.com" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="发件人"
            >
            <el-input v-model="form.fromName" placeholder="发件人显示名称" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="认证/加密"
        >
        <el-checkbox v-model="form.auth" :true-label="1" :false-label="0">认证</el-checkbox>
        <el-checkbox v-model="form.starttls" :true-label="1" :false-label="0">STARTTLS</el-checkbox>
        <el-checkbox v-model="form.ssl" :true-label="1" :false-label="0">SSL</el-checkbox>
      </el-form-item>

      <el-form-item label="状态"
        >
        <el-radio-group v-model="form.status"
          >
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="loading" @click="handleTest">测试连接</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
