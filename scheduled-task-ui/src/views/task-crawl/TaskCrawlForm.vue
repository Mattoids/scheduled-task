<script setup lang="ts">
import { ref, reactive, watch, onMounted, onBeforeUnmount, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'
import { getTaskCrawl, createTaskCrawl, updateTaskCrawl, previewRewriteTaskCrawl, previewJsonTaskCrawl } from '@/api/taskCrawl'
import { useUserStore } from '@/stores/user'
import type { SshHopConfig, TaskWebCrawlConfig, TaskWebCrawlSelector } from '@/types/entity'

const props = defineProps<{
  visible: boolean
  id?: number
  templateOptions: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref()
const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewContent = ref('')
const previewUrl = ref('')
const previewInfo = ref({ statusCode: undefined as number | undefined, title: undefined as string | undefined, length: 0 })
const previewTitle = ref('')
const previewMode = ref<'web' | 'json'>('web')
const previewSelectorEnabled = ref<number>(1)
const previewJson = ref<any>(null)
const previewJsonString = computed(() => {
  try {
    return JSON.stringify(previewJson.value, null, 2)
  } catch {
    return String(previewJson.value)
  }
})

const defaultSelector = (): TaskWebCrawlSelector => ({
  selectorType: 'CSS',
  selectorValue: '',
  fieldName: '',
  attribute: 'text',
  dataType: 'STRING',
  isRowSelector: 0,
})

const defaultHop = (): SshHopConfig => ({
  host: '',
  port: 22,
  username: '',
  password: '',
  privateKey: '',
  passphrase: '',
  authType: 'PASSWORD',
})

const handleAddHop = () => {
  if (!form.sshHops) {
    form.sshHops = []
  }
  form.sshHops.push(defaultHop())
}

const handleRemoveHop = (index: number) => {
  form.sshHops?.splice(index, 1)
}

const form = reactive<TaskWebCrawlConfig>({
  crawlName: '',
  crawlCode: '',
  requestUrl: '',
  requestMethod: 'GET',
  requestHeaders: '',
  requestParams: '',
  requestBody: '',
  requestContentType: '',
  cookies: '',
  authType: 'NONE',
  authConfig: '',
  sshEnabled: 0,
  sshHost: '',
  sshPort: 22,
  sshUsername: '',
  sshPassword: '',
  sshPrivateKey: '',
  sshPassphrase: '',
  sshAuthType: 'PASSWORD',
  sshRemoteHost: '',
  sshRemotePort: 80,
  sshLocalPort: 0,
  sshJumpHostEnabled: 0,
  sshHops: [],
  proxyEnabled: 0,
  proxyHost: '',
  proxyPort: undefined,
  proxyUsername: '',
  proxyPassword: '',
  renderType: 'STATIC',
  driverConfig: '',
  outputFormat: 'CSV',
  templateCode: '',
  fileSuffix: '',
  fileNamePattern: '',
  excelSheetName: '',
  description: '',
  customParams: '',
  status: 1,
  paginationEnabled: 0,
  paginationType: 'SELECTOR',
  paginationSelector: '',
  paginationUrlTemplate: '',
  paginationMaxPages: 1,
  mediaEnabled: 0,
  mediaSelector: '',
  mediaFileTypes: 'image',
  mediaOutputMode: 'ATTACH',
  mediaZipNamePattern: '',
  mediaFilterConfig: '',
  chartEnabled: 0,
  chartType: 'BAR',
  chartTitle: '',
  chartAutoMerge: 1,
  chartLabelRotation: 'AUTO',
  chartBackgroundColor: '',
  chartFontFamily: '',
  chartFontSize: undefined as any,
  selectors: [],
})

const rules = {
  crawlName: [{ required: true, message: '请输入爬取名称', trigger: 'blur' }],
  crawlCode: [{ required: true, message: '请输入爬取编码', trigger: 'blur' }],
  requestUrl: [{ required: true, message: '请输入请求 URL', trigger: 'blur' }],
}

const authTypeOptions = [
  { label: '无', value: 'NONE' },
  { label: 'Basic', value: 'BASIC' },
  { label: 'Token', value: 'TOKEN' },
  { label: 'OAuth2', value: 'OAUTH2' },
]

const renderTypeOptions = [
  { label: '静态 HTML', value: 'STATIC' },
  { label: '动态渲染（Selenium）', value: 'DYNAMIC' },
]

const chartFontFamilyOptions = [
  { label: '默认', value: '' },
  { label: '微软雅黑', value: 'Microsoft YaHei' },
  { label: '宋体', value: 'SimSun' },
  { label: '黑体', value: 'SimHei' },
  { label: '楷体', value: 'KaiTi' },
  { label: '仿宋', value: 'FangSong' },
  { label: '无衬线', value: 'SansSerif' },
  { label: '衬线', value: 'Serif' },
  { label: '等宽', value: 'Monospaced' },
]

const selectorTypeOptions = [
  { label: 'CSS', value: 'CSS' },
  { label: 'XPath', value: 'XPATH' },
  { label: 'Regex', value: 'REGEX' },
  { label: 'JSON', value: 'JSON' },
  { label: '模糊匹配', value: 'FUZZY' },
]

const selectorValuePlaceholder = (type?: string) => {
  switch (type) {
    case 'JSON':
      return 'JSON 路径，如 $.data.list[0].name'
    case 'FUZZY':
      return 'CSS选择器|匹配文本，如 a|登录'
    case 'XPATH':
      return 'XPath 表达式'
    case 'REGEX':
      return '正则表达式'
    default:
      return '选择器表达式'
  }
}

const dataTypeOptions = [
  { label: '文本', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' },
  { label: '链接', value: 'LINK' },
  { label: 'HTML', value: 'HTML' },
]

const loadDetail = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const res = await getTaskCrawl(props.id)
    Object.assign(form, res)
    if (!form.selectors || form.selectors.length === 0) {
      form.selectors = [defaultSelector()]
    }
    form.sshHops = Array.isArray(res.sshHops) ? res.sshHops : []
  } finally {
    loading.value = false
  }
}

const handleAddSelector = () => {
  form.selectors!.push(defaultSelector())
}

const handleRemoveSelector = (index: number) => {
  form.selectors!.splice(index, 1)
}

const handleSave = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (props.id) {
      await updateTaskCrawl(props.id, form)
    } else {
      await createTaskCrawl(form)
    }
    ElMessage.success('保存成功')
    emit('success')
  } finally {
    saving.value = false
  }
}

const handleCopyPreview = async () => {
  const text = previewMode.value === 'web' ? previewContent.value : previewJsonString.value
  if (!text) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('复制成功')
  } catch (e: any) {
    ElMessage.error(`复制失败：${e?.message || '未知错误'}`)
  }
}

const handlePreview = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.requestUrl) {
    ElMessage.warning('请输入请求 URL')
    return
  }
  previewLoading.value = true
  previewVisible.value = true
  previewing.value = true
  form.previewSelectorEnabled = previewSelectorEnabled.value
  try {
    if (previewMode.value === 'web') {
      const html = await previewRewriteTaskCrawl(form)
      const userStore = useUserStore()
      if (userStore.token) {
        document.cookie = `accessToken=${userStore.token}; path=/; max-age=600`
      }
      revokePreviewUrl()
      previewContent.value = html || ''
      previewUrl.value = URL.createObjectURL(new Blob([html || ''], { type: 'text/html' }))
      previewInfo.value = {
        statusCode: undefined,
        title: undefined,
        length: (html || '').length,
      }
      previewTitle.value = '网页预览'
    } else {
      const res = await previewJsonTaskCrawl(form)
      if (!res.success) {
        ElMessage.error(`JSON 预览失败：${res.message || '未知错误'}`)
        previewVisible.value = false
        return
      }
      previewJson.value = res.data ?? null
      previewInfo.value = {
        statusCode: res.statusCode,
        title: res.title,
        length: previewJsonString.value.length,
      }
      previewTitle.value = res.title ? `${res.title} - JSON 预览` : 'JSON 预览'
    }
  } catch (e: any) {
    ElMessage.error(`预览失败：${e?.message || '未知错误'}`)
    previewVisible.value = false
  } finally {
    previewLoading.value = false
    previewing.value = false
  }
}

watch(previewMode, () => {
  if (previewVisible.value) {
    handlePreview()
  }
})

watch(previewSelectorEnabled, () => {
  if (previewVisible.value) {
    handlePreview()
  }
})

const revokePreviewUrl = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

watch(previewVisible, (visible) => {
  if (!visible) {
    revokePreviewUrl()
  }
})

onBeforeUnmount(() => {
  revokePreviewUrl()
})

const handleClose = () => {
  emit('update:visible', false)
}

const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    crawlName: '',
    crawlCode: '',
    requestUrl: '',
    requestMethod: 'GET',
    requestHeaders: '',
    requestParams: '',
    requestBody: '',
    requestContentType: '',
    cookies: '',
    authType: 'NONE',
    authConfig: '',
    sshEnabled: 0,
    sshHost: '',
    sshPort: 22,
    sshUsername: '',
    sshPassword: '',
    sshPrivateKey: '',
    sshPassphrase: '',
    sshAuthType: 'PASSWORD',
    sshRemoteHost: '',
    sshRemotePort: 80,
    sshLocalPort: 0,
    sshJumpHostEnabled: 0,
    sshHops: [],
    proxyEnabled: 0,
    proxyHost: '',
    proxyPort: undefined,
    proxyUsername: '',
    proxyPassword: '',
    renderType: 'STATIC',
    driverConfig: '',
    outputFormat: 'CSV',
    templateCode: '',
    fileSuffix: '',
    fileNamePattern: '',
    excelSheetName: '',
    description: '',
    customParams: '',
    status: 1,
    paginationEnabled: 0,
    paginationType: 'SELECTOR',
    paginationSelector: '',
    paginationUrlTemplate: '',
    paginationMaxPages: 1,
    mediaEnabled: 0,
    mediaSelector: '',
    mediaFileTypes: 'image',
    mediaOutputMode: 'ATTACH',
    mediaZipNamePattern: '',
    mediaFilterConfig: '',
    chartEnabled: 0,
    chartType: 'BAR',
    chartTitle: '',
    chartAutoMerge: 1,
    chartLabelRotation: 'AUTO',
    chartBackgroundColor: '',
    chartFontFamily: '',
    chartFontSize: undefined as any,
    selectors: [defaultSelector()],
  })
  formRef.value?.resetFields()
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
  },
  { immediate: true }
)
</script>

<template>
  <el-dialog
    v-model="props.visible"
    :title="props.id ? '编辑爬取配置' : '新增爬取配置'"
    width="900px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="120px"
      class="crawl-form"
    >
      <el-tabs>
        <el-tab-pane label="基础信息">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="爬取名称" prop="crawlName">
                <el-input v-model="form.crawlName" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="爬取编码" prop="crawlCode">
                <el-input v-model="form.crawlCode" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="请求 URL" prop="requestUrl">
            <el-input v-model="form.requestUrl" placeholder="支持 ${变量}" />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="请求方法">
                <el-select v-model="form.requestMethod">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="Content-Type">
                <el-input v-model="form.requestContentType" placeholder="application/json" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="请求头 Headers">
            <el-input
              v-model="form.requestHeaders"
              type="textarea"
              :rows="3"
              placeholder='JSON 格式，如 {"X-Api-Key": "xxx"}'
            />
          </el-form-item>
          <el-form-item label="URL 参数">
            <el-input
              v-model="form.requestParams"
              type="textarea"
              :rows="2"
              placeholder='JSON 格式，如 {"page": "1"}'
            />
          </el-form-item>
          <el-form-item label="请求 Body">
            <el-input
              v-model="form.requestBody"
              type="textarea"
              :rows="4"
              placeholder="支持 ${变量}"
            />
          </el-form-item>
          <el-form-item label="Cookie">
            <el-input
              v-model="form.cookies"
              type="textarea"
              :rows="2"
              placeholder='JSON 格式，保存时会自动加密'
            />
          </el-form-item>
          <el-divider content-position="left">代理</el-divider>
          <el-form-item label="启用代理">
            <el-switch v-model="form.proxyEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <template v-if="form.proxyEnabled === 1">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="代理主机">
                  <el-input v-model="form.proxyHost" placeholder="127.0.0.1" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="代理端口">
                  <el-input-number v-model="form.proxyPort" :min="1" :max="65535" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="代理用户名">
                  <el-input v-model="form.proxyUsername" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="代理密码">
                  <el-input v-model="form.proxyPassword" type="password" show-password />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
        </el-tab-pane>

        <el-tab-pane label="认证">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="认证方式">
                <el-select v-model="form.authType">
                  <el-option
                    v-for="item in authTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="认证配置">
            <el-input
              v-model="form.authConfig"
              type="textarea"
              :rows="4"
              placeholder='JSON 格式，如 {"username": "", "password": ""}，保存时自动加密'
            />
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane label="ssh配置">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="说明"
            description="启用 SSH 隧道后，目标服务会被映射到本地 127.0.0.1:<本地端口>；开启跳板机时，先连接跳板机，再转发到内网目标服务器。"
            style="margin-bottom: 16px"
          />
          <el-form-item label="启用 SSH 隧道">
            <el-switch v-model="form.sshEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <template v-if="form.sshEnabled === 1">
            <el-divider content-position="left">SSH 服务器</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="SSH 服务器">
                  <el-input v-model="form.sshHost" placeholder="服务器地址，如 192.168.1.10" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="SSH 端口">
                  <el-input-number v-model="form.sshPort" :min="1" :max="65535" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="SSH 用户名">
              <el-input v-model="form.sshUsername" />
            </el-form-item>
            <el-form-item label="认证方式">
              <el-radio-group v-model="form.sshAuthType">
                <el-radio-button label="PASSWORD">密码</el-radio-button>
                <el-radio-button label="KEY">私钥</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.sshAuthType === 'PASSWORD'" label="SSH 密码">
              <el-input v-model="form.sshPassword" type="password" show-password />
            </el-form-item>
            <template v-if="form.sshAuthType === 'KEY'">
              <el-form-item label="SSH 私钥">
                <el-input
                  v-model="form.sshPrivateKey"
                  type="textarea"
                  :rows="4"
                  placeholder="保存时自动加密"
                />
              </el-form-item>
              <el-form-item label="私钥口令">
                <el-input v-model="form.sshPassphrase" type="password" show-password />
              </el-form-item>
            </template>

            <el-divider content-position="left">目标服务（留空则从请求 URL 自动提取）</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="目标服务器主机">
                  <el-input v-model="form.sshRemoteHost" placeholder="内网目标主机，如 10.0.0.5" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="目标服务器端口">
                  <el-input-number v-model="form.sshRemotePort" :min="1" :max="65535" placeholder="请求 URL 端口" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="本地映射端口">
                  <el-input-number v-model="form.sshLocalPort" :min="0" :max="65535" placeholder="0=自动分配" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="跳板机/代理链">
              <el-switch v-model="form.sshJumpHostEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <template v-if="form.sshJumpHostEnabled === 1">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="链路说明"
                description="按从服务侧到请求侧配置中转服务器：越靠上越靠近服务所在机器，越靠下越靠近请求方（最外层代理）。"
                style="margin-bottom: 16px"
              />
              <div
                v-for="(hop, index) in form.sshHops"
                :key="index"
                class="hop-row"
              >
                <el-divider content-position="left">
                  中转节点 {{ index + 1 }}
                  <span v-if="index === 0">（最靠近服务）</span>
                  <span v-else-if="index === form.sshHops!.length - 1">（最外层代理）</span>
                </el-divider>
                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-form-item label="服务器地址">
                      <el-input v-model="hop.host" :placeholder="`中转节点 ${index + 1} 地址，如 192.168.1.10`" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="SSH 端口">
                      <el-input-number v-model="hop.port" :min="1" :max="65535" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="用户名">
                  <el-input v-model="hop.username" />
                </el-form-item>
                <el-form-item label="认证方式">
                  <el-radio-group v-model="hop.authType">
                    <el-radio-button label="PASSWORD">密码</el-radio-button>
                    <el-radio-button label="KEY">私钥</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item v-if="hop.authType === 'PASSWORD'" label="密码">
                  <el-input v-model="hop.password" type="password" show-password />
                </el-form-item>
                <template v-if="hop.authType === 'KEY'">
                  <el-form-item label="私钥">
                    <el-input
                      v-model="hop.privateKey"
                      type="textarea"
                      :rows="4"
                      placeholder="保存时自动加密"
                    />
                  </el-form-item>
                  <el-form-item label="私钥口令">
                    <el-input v-model="hop.passphrase" type="password" show-password />
                  </el-form-item>
                </template>
                <el-button type="danger" @click="handleRemoveHop(index)">删除该节点</el-button>
              </div>
              <el-button type="primary" @click="handleAddHop">新增中转节点</el-button>
            </template>
          </template>
        </el-tab-pane>

        <el-tab-pane label="渲染与选择器">
          <el-form-item label="渲染模式">
            <el-radio-group v-model="form.renderType">
              <el-radio-button
                v-for="item in renderTypeOptions"
                :key="item.value"
                :label="item.value"
              >
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.renderType === 'DYNAMIC'" label="WebDriver 配置">
            <el-input
              v-model="form.driverConfig"
              type="textarea"
              :rows="4"
              placeholder='JSON 格式，如 {"waitSelector": "#list", "windowSize": "1920,1080"}'
            />
          </el-form-item>
          <el-divider content-position="left">字段提取规则</el-divider>
          <div
            v-for="(selector, index) in form.selectors"
            :key="index"
            class="selector-row"
          >
            <el-row :gutter="10">
              <el-col :span="4">
                <el-input v-model="selector.fieldName" placeholder="字段名" />
              </el-col>
              <el-col :span="4">
                <el-select v-model="selector.selectorType">
                  <el-option
                    v-for="item in selectorTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-col>
              <el-col :span="7">
                <el-input v-model="selector.selectorValue" :placeholder="selectorValuePlaceholder(selector.selectorType)" />
              </el-col>
              <el-col :span="3">
                <el-input v-model="selector.attribute" placeholder="属性" />
              </el-col>
              <el-col :span="3">
                <el-select v-model="selector.dataType">
                  <el-option
                    v-for="item in dataTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-col>
              <el-col :span="2">
                <el-checkbox v-model="selector.isRowSelector" :true-label="1" :false-label="0">行</el-checkbox>
              </el-col>
              <el-col :span="3">
                <el-button type="danger" @click="handleRemoveSelector(index)">删除</el-button>
              </el-col>
            </el-row>
          </div>
          <el-button type="primary" @click="handleAddSelector">新增选择器</el-button>
        </el-tab-pane>

        <el-tab-pane label="分页与媒体">
          <el-form-item label="启用分页">
            <el-switch v-model="form.paginationEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <template v-if="form.paginationEnabled === 1">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="分页类型">
                  <el-select v-model="form.paginationType">
                    <el-option label="选择器" value="SELECTOR" />
                    <el-option label="URL 模板" value="URL_TEMPLATE" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="最大页数">
                  <el-input-number v-model="form.paginationMaxPages" :min="1" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="下一页选择器" v-if="form.paginationType === 'SELECTOR'">
              <el-input v-model="form.paginationSelector" />
            </el-form-item>
            <el-form-item label="分页 URL 模板" v-if="form.paginationType === 'URL_TEMPLATE'">
              <el-input v-model="form.paginationUrlTemplate" placeholder="/list?page=${page}" />
            </el-form-item>
          </template>
          <el-divider />
          <el-form-item label="下载媒体">
            <el-switch v-model="form.mediaEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <template v-if="form.mediaEnabled === 1">
            <el-form-item label="媒体选择器">
              <el-input v-model="form.mediaSelector" placeholder="img,video,audio,source" />
            </el-form-item>
            <el-form-item label="文件类型">
              <el-input v-model="form.mediaFileTypes" placeholder="image,video,audio" />
            </el-form-item>
            <el-form-item label="输出模式">
              <el-select v-model="form.mediaOutputMode">
                <el-option label="附件" value="ATTACH" />
                <el-option label="ZIP 附件" value="ZIP" />
                <el-option label="仅存储" value="STORE_ONLY" />
                <el-option label="ZIP + 存储" value="ATTACH_ZIP" />
              </el-select>
            </el-form-item>
            <el-form-item label="筛选配置">
              <el-input
                v-model="form.mediaFilterConfig"
                type="textarea"
                :rows="3"
                placeholder='{"maxFileSizeBytes": 10485760, "minWidth": 200, "minHeight": 200}'
              />
            </el-form-item>
          </template>
        </el-tab-pane>

        <el-tab-pane label="输出与图表">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="输出格式">
                <el-select v-model="form.outputFormat">
                  <el-option label="CSV" value="CSV" />
                  <el-option label="Excel" value="EXCEL" />
                  <el-option label="Word" value="WORD" />
                  <el-option label="PPT" value="PPT" />
                  <el-option label="TXT" value="TXT" />
                  <el-option label="内联通知" value="INLINE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="模板">
                <el-select v-model="form.templateCode" clearable>
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
          <el-form-item label="文件名模式">
            <el-input v-model="form.fileNamePattern" />
          </el-form-item>
          <el-form-item v-if="form.outputFormat === 'EXCEL'" label="Sheet 名称">
            <el-input v-model="form.excelSheetName" placeholder="留空使用爬取名称，支持 {yyyyMMdd} 等内置变量" />
          </el-form-item>
          <el-form-item label="启用图表">
            <el-switch v-model="form.chartEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <template v-if="form.chartEnabled === 1">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="图表类型">
                  <el-select v-model="form.chartType">
                    <el-option label="柱状图" value="BAR" />
                    <el-option label="折线图" value="LINE" />
                    <el-option label="饼图" value="PIE" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="图表标题">
                  <el-input v-model="form.chartTitle" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="字体">
                  <el-select v-model="form.chartFontFamily" placeholder="默认字体" clearable style="width: 100%">
                    <el-option v-for="item in chartFontFamilyOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="字号">
                  <el-input-number v-model="form.chartFontSize" :min="8" :max="72" placeholder="默认" style="width: 100%" controls-position="right" />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button :loading="previewing" @click="handlePreview">预览</el-button>
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </span>
    </template>
  </el-dialog>

  <el-dialog
    v-model="previewVisible"
    :title="previewTitle || '网页预览'"
    width="90%"
    top="5vh"
    destroy-on-close
  >
    <div class="preview-toolbar">
      <el-radio-group v-model="previewMode" size="small">
        <el-radio-button label="web">网页</el-radio-button>
        <el-radio-button label="json">JSON</el-radio-button>
      </el-radio-group>
      <div class="preview-actions">
        <div class="selector-toggle">
          <el-switch
            v-model="previewSelectorEnabled"
            :active-value="1"
            :inactive-value="0"
            active-text="选择器"
            inactive-text="选择器"
            inline-prompt
            size="small"
          />
        </div>
      </div>
    </div>
    <div v-if="previewInfo.length" class="preview-info">
      <span v-if="previewInfo.statusCode" class="info-item">状态码：{{ previewInfo.statusCode }}</span>
      <span v-if="previewInfo.title" class="info-item">标题：{{ previewInfo.title }}</span>
      <span class="info-item">内容长度：{{ previewInfo.length }} 字符</span>
    </div>
    <div
      v-loading="previewLoading"
      element-loading-text="加载中..."
      class="preview-content-wrapper"
    >
      <iframe
        v-if="previewMode === 'web'"
        :src="previewUrl"
        style="width: 100%; height: 70vh; border: 1px solid #dcdfe6; border-radius: 4px"
      />
      <pre v-else class="json-preview">{{ previewJsonString }}</pre>
      <el-tooltip content="复制" placement="top">
        <el-button
          class="copy-btn"
          type="primary"
          size="small"
          circle
          :icon="DocumentCopy"
          @click="handleCopyPreview"
        />
      </el-tooltip>
    </div>
  </el-dialog>
</template>

<style scoped>
.selector-row {
  margin-bottom: 12px;
}
.hop-row {
  margin-bottom: 20px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}
.hop-row .el-divider {
  margin-top: 0;
}
.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.preview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.selector-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
}
.preview-content-wrapper {
  position: relative;
  min-height: 70vh;
}
.copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 10;
}
.preview-info {
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}
.preview-info .info-item {
  margin-right: 16px;
}
.json-preview {
  width: 100%;
  height: 70vh;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
