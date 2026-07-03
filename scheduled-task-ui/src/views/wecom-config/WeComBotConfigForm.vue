<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createWeComBotConfig,
  getWeComBotConfig,
  updateWeComBotConfig,
  testWeComBotConfig,
} from '@/api/weComBotConfig'
import type { WeComBotConfig } from '@/types/entity'

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
const form = ref<WeComBotConfig>({
  configName: '',
  webhookKey: '',
  status: 1,
})

const rules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  webhookKey: [{ required: true, message: '请输入 Webhook Key', trigger: 'blur' }],
}

const isEdit = computed(() => !!props.id)
const title = computed(() => (isEdit.value ? '编辑企业微信群机器人配置' : '新增企业微信群机器人配置'))

const resetForm = () => {
  form.value = {
    configName: '',
    webhookKey: '',
    status: 1,
  }
}

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res = await getWeComBotConfig(props.id)
    form.value = { ...res }
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
    if (isEdit.value) {
      await updateWeComBotConfig(props.id!, data)
    } else {
      await createWeComBotConfig(data)
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
    const res = await testWeComBotConfig({ ...form.value })
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
  <el-dialog v-model="dialogVisible" :title="title" width="600px"
    >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" v-loading="loading"
      >
      <el-form-item label="配置名称" prop="configName"
        >
        <el-input v-model="form.configName" placeholder="配置名称" />
      </el-form-item>

      <el-form-item label="Webhook Key" prop="webhookKey"
        >
        <el-input v-model="form.webhookKey" placeholder="群机器人 Webhook Key" />
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
