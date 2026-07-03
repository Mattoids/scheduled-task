<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createWeComAppConfig,
  getWeComAppConfig,
  updateWeComAppConfig,
  testWeComAppConfig,
} from '@/api/weComAppConfig'
import type { WeComAppConfig } from '@/types/entity'

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
const form = ref<WeComAppConfig>({
  configName: '',
  corpId: '',
  agentId: 0,
  secret: '',
  token: '',
  aesKey: '',
  status: 1,
  menuJson: '',
})

const rules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  corpId: [{ required: true, message: '请输入企业 ID', trigger: 'blur' }],
  agentId: [{ required: true, message: '请输入应用 ID', trigger: 'blur' }],
  secret: [{ required: true, message: '请输入 Secret', trigger: 'blur' }],
}

const isEdit = computed(() => !!props.id)
const title = computed(() => (isEdit.value ? '编辑企业微信应用配置' : '新增企业微信应用配置'))

const resetForm = () => {
  form.value = {
    configName: '',
    corpId: '',
    agentId: 0,
    secret: '',
    token: '',
    aesKey: '',
    status: 1,
    menuJson: '',
  }
}

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res = await getWeComAppConfig(props.id)
    form.value = { ...res, secret: '' }
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
    if (isEdit.value && !data.secret) {
      delete data.secret
    }
    if (isEdit.value) {
      await updateWeComAppConfig(props.id!, data)
    } else {
      await createWeComAppConfig(data)
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
    if (isEdit.value && props.id && !data.secret) {
      const detail = await getWeComAppConfig(props.id)
      data.secret = detail.secret
    }
    const res = await testWeComAppConfig(data)
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
          <el-form-item label="企业 ID" prop="corpId"
            >
            <el-input v-model="form.corpId" placeholder="企业微信 CorpID" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="应用 ID" prop="agentId"
            >
            <el-input-number v-model="form.agentId" :min="0" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="Secret" prop="secret"
        >
        <el-input v-model="form.secret" type="password" placeholder="留空表示不修改" show-password />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="Token"
            >
            <el-input v-model="form.token" placeholder="回调 Token（可选）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="AES Key"
            >
            <el-input v-model="form.aesKey" placeholder="回调 AES Key（可选）" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="菜单 JSON"
        >
        <el-input
          v-model="form.menuJson"
          type="textarea"
          :rows="4"
          placeholder='{"button":[{"type":"click","name":"查询任务","key":"QUERY_TASKS"}]}'
        />
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
