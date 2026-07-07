<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskCrawl, createTaskCrawl, updateTaskCrawl, previewTaskCrawl } from '@/api/taskCrawl'
import type { TaskWebCrawlConfig, TaskWebCrawlSelector } from '@/types/entity'

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

const defaultSelector = (): TaskWebCrawlSelector => ({
  selectorType: 'CSS',
  selectorValue: '',
  fieldName: '',
  attribute: 'text',
  dataType: 'STRING',
  isRowSelector: 0,
})

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
  renderType: 'STATIC',
  driverConfig: '',
  outputFormat: 'CSV',
  templateCode: '',
  fileSuffix: '',
  fileNamePattern: '',
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

const selectorTypeOptions = [
  { label: 'CSS', value: 'CSS' },
  { label: 'XPath', value: 'XPATH' },
  { label: 'Regex', value: 'REGEX' },
]

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

const handlePreview = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.requestUrl) {
    ElMessage.warning('请输入请求 URL')
    return
  }
  previewing.value = true
  try {
    const res = await previewTaskCrawl(form)
    if (res.success) {
      ElMessage.success(`预览成功：${res.title || res.message || ''}`)
    } else {
      ElMessage.error(`预览失败：${res.message || '未知错误'}`)
    }
  } catch (e: any) {
    ElMessage.error(`预览失败：${e?.message || '未知错误'}`)
  } finally {
    previewing.value = false
  }
}

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
    renderType: 'STATIC',
    driverConfig: '',
    outputFormat: 'CSV',
    templateCode: '',
    fileSuffix: '',
    fileNamePattern: '',
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
        </el-tab-pane>

        <el-tab-pane label="认证与 SSH">
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
          <el-divider />
          <el-form-item label="启用 SSH 隧道">
            <el-switch v-model="form.sshEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <template v-if="form.sshEnabled === 1">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="SSH 主机">
                  <el-input v-model="form.sshHost" />
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
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="远程目标主机">
                  <el-input v-model="form.sshRemoteHost" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="远程目标端口">
                  <el-input-number v-model="form.sshRemotePort" :min="1" :max="65535" />
                </el-form-item>
              </el-col>
            </el-row>
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
                <el-input v-model="selector.selectorValue" placeholder="选择器表达式" />
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
</template>

<style scoped>
.selector-row {
  margin-bottom: 12px;
}
</style>
