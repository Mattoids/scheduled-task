<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createStorageConfig,
  getStorageConfig,
  updateStorageConfig,
  testStorageConfig,
} from '@/api/storageConfig'
import type { StorageConfig } from '@/types/entity'

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

const defaultConfigJson = (type: string) => {
  switch (type) {
    case 'LOCAL':
      return {
        storagePath: '',
        baseUrl: '',
      }
    case 'OSS':
      return {
        endpoint: '',
        accessKeyId: '',
        accessKeySecret: '',
        bucketName: '',
        prefix: '',
        https: true,
        signedUrlExpires: undefined,
      }
    case 'S3':
      return {
        endpoint: '',
        accessKeyId: '',
        secretAccessKey: '',
        region: '',
        bucketName: '',
        prefix: '',
        pathStyleAccess: false,
        signedUrlExpires: undefined,
      }
    case 'WEBDAV':
      return {
        url: '',
        username: '',
        password: '',
        prefix: '',
        baseUrl: '',
      }
    default:
      return {}
  }
}

const form = ref<StorageConfig>({
  configName: '',
  storageType: 'LOCAL',
  configJson: defaultConfigJson('LOCAL'),
  status: 1,
  isDefault: 0,
})

const typeOptions = [
  { label: '本地存储', value: 'LOCAL' },
  { label: '阿里云 OSS', value: 'OSS' },
  { label: 'S3 / MinIO', value: 'S3' },
  { label: 'WebDAV', value: 'WEBDAV' },
]

const rules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  storageType: [{ required: true, message: '请选择存储类型', trigger: 'change' }],
}

const isEdit = computed(() => !!props.id)
const title = computed(() => (isEdit.value ? '编辑存储配置' : '新增存储配置'))

const resetForm = () => {
  form.value = {
    configName: '',
    storageType: 'LOCAL',
    configJson: defaultConfigJson('LOCAL'),
    status: 1,
    isDefault: 0,
  }
}

const parseConfigJson = (value: any) => {
  if (!value) return {}
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      return {}
    }
  }
  return value
}

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res = await getStorageConfig(props.id)
    form.value = {
      ...res,
      configJson: parseConfigJson(res.configJson),
    }
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

watch(
  () => form.value.storageType,
  (type) => {
    if (!isEdit.value && type) {
      form.value.configJson = defaultConfigJson(type)
    }
  }
)

const buildSubmitData = () => {
  const data = { ...form.value }
  data.configJson =
    typeof data.configJson === 'string'
      ? data.configJson
      : JSON.stringify(data.configJson)
  return data
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = buildSubmitData()
    if (isEdit.value) {
      await updateStorageConfig(props.id!, data)
    } else {
      await createStorageConfig(data)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    emit('success')
  } finally {
    loading.value = false
  }
}

const handleTest = async () => {
  if (!props.id) {
    ElMessage.warning('请先保存配置再测试')
    return
  }
  loading.value = true
  try {
    const message = await testStorageConfig(props.id)
    ElMessage.success(message || '测试成功')
  } catch (e: any) {
    ElMessage.error(e.message || '测试失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="700px">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="dialog-form"
      label-width="120px"
      v-loading="loading"
    >
      <el-form-item label="配置名称" prop="configName">
        <el-input v-model="form.configName" placeholder="配置名称" />
      </el-form-item>

      <el-form-item label="存储类型" prop="storageType">
        <el-select
          v-model="form.storageType"
          placeholder="请选择存储类型"
          :disabled="isEdit"
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

      <template v-if="form.storageType === 'LOCAL'">
        <el-form-item label="存储目录">
          <el-input
            v-model="form.configJson.storagePath"
            placeholder="相对于 report.upload.path 或绝对路径"
          />
        </el-form-item>
        <el-form-item label="访问基础 URL">
          <el-input
            v-model="form.configJson.baseUrl"
            placeholder="例如 http://localhost:1236/storage"
          />
        </el-form-item>
      </template>

      <template v-if="form.storageType === 'OSS'">
        <el-form-item label="Endpoint" required>
          <el-input
            v-model="form.configJson.endpoint"
            placeholder="例如 https://oss-cn-hangzhou.aliyuncs.com"
          />
        </el-form-item>
        <el-form-item label="Access Key ID" required>
          <el-input v-model="form.configJson.accessKeyId" placeholder="Access Key ID" />
        </el-form-item>
        <el-form-item label="Access Key Secret" required>
          <el-input
            v-model="form.configJson.accessKeySecret"
            type="password"
            placeholder="Access Key Secret"
            show-password
          />
        </el-form-item>
        <el-form-item label="Bucket" required>
          <el-input v-model="form.configJson.bucketName" placeholder="Bucket 名称" />
        </el-form-item>
        <el-form-item label="路径前缀">
          <el-input
            v-model="form.configJson.prefix"
            placeholder="例如 scheduled-task/reports"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="HTTPS">
              <el-switch
                v-model="form.configJson.https"
                :active-value="true"
                :inactive-value="false"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签名 URL 过期">
              <el-input-number
                v-model="form.configJson.signedUrlExpires"
                :min="0"
                controls-position="right"
                placeholder="秒"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <template v-if="form.storageType === 'S3'">
        <el-form-item label="Endpoint" required>
          <el-input
            v-model="form.configJson.endpoint"
            placeholder="例如 https://s3.amazonaws.com 或 MinIO 地址"
          />
        </el-form-item>
        <el-form-item label="Access Key ID" required>
          <el-input v-model="form.configJson.accessKeyId" placeholder="Access Key ID" />
        </el-form-item>
        <el-form-item label="Secret Access Key" required>
          <el-input
            v-model="form.configJson.secretAccessKey"
            type="password"
            placeholder="Secret Access Key"
            show-password
          />
        </el-form-item>
        <el-form-item label="Region" required>
          <el-input v-model="form.configJson.region" placeholder="例如 us-east-1" />
        </el-form-item>
        <el-form-item label="Bucket" required>
          <el-input v-model="form.configJson.bucketName" placeholder="Bucket 名称" />
        </el-form-item>
        <el-form-item label="路径前缀">
          <el-input v-model="form.configJson.prefix" placeholder="例如 reports" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="路径样式访问">
              <el-switch
                v-model="form.configJson.pathStyleAccess"
                :active-value="true"
                :inactive-value="false"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签名 URL 过期">
              <el-input-number
                v-model="form.configJson.signedUrlExpires"
                :min="0"
                controls-position="right"
                placeholder="秒"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <template v-if="form.storageType === 'WEBDAV'">
        <el-form-item label="WebDAV 地址" required>
          <el-input
            v-model="form.configJson.url"
            placeholder="例如 https://nas.example.com/dav/reports"
          />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="form.configJson.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input
            v-model="form.configJson.password"
            type="password"
            placeholder="密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="路径前缀">
          <el-input v-model="form.configJson.prefix" placeholder="例如 reports" />
        </el-form-item>
        <el-form-item label="访问基础 URL">
          <el-input
            v-model="form.configJson.baseUrl"
            placeholder="用于替换 WebDAV 路径为外部下载地址"
          />
        </el-form-item>
      </template>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="默认配置">
            <el-switch
              v-model="form.isDefault"
              :active-value="1"
              :inactive-value="0"
              active-text="是"
              inactive-text="否"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用状态">
            <el-switch
              v-model="form.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="禁用"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="loading" @click="handleTest">测试连接</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit"
        >确定</el-button
      >
    </template>
  </el-dialog>
</template>
