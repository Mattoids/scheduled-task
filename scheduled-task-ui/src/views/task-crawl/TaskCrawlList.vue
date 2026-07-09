<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import { pageTaskCrawl, deleteTaskCrawl, openCrawlSshTunnel, closeCrawlSshTunnel, getCrawlSshTunnelStatus, type CrawlSshTunnelInfo } from '@/api/taskCrawl'
import { listTemplate } from '@/api/template'
import TaskCrawlForm from './TaskCrawlForm.vue'
import type { TaskWebCrawlConfig } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '网页爬取' }])

const { current, size, total, records, buildQuery, setPageResult, reset } = usePagination()

const queryForm = reactive({
  crawlName: '',
  crawlCode: '',
})

const loading = ref(false)
const formVisible = ref(false)
const formId = ref<number | undefined>(undefined)
const templateOptions = ref<{ label: string; value: string }[]>([])
const tunnelMap = reactive<Record<number, CrawlSshTunnelInfo>>({})

const loadTunnelStatus = async (id: number) => {
  try {
    tunnelMap[id] = await getCrawlSshTunnelStatus(id)
  } catch {
    tunnelMap[id] = { connected: false }
  }
}

const handleToggleSshTunnel = async (row: TaskWebCrawlConfig) => {
  const id = row.id!
  const current = tunnelMap[id]
  if (current?.connected) {
    await closeCrawlSshTunnel(id)
    ElMessage.success('SSH 隧道已关闭')
    await loadTunnelStatus(id)
  } else {
    const res = await openCrawlSshTunnel(id)
    tunnelMap[id] = res
    ElMessage.success(res.message || `SSH 隧道已开启: 127.0.0.1:${res.localPort}`)
  }
}

const openLocalUrl = (row: TaskWebCrawlConfig) => {
  const url = tunnelMap[row.id!]?.localUrl
  if (url) {
    window.open(url, '_blank')
  }
}

const loadOptions = async () => {
  const tpl = await listTemplate({ size: 1000 }).catch(() => ({ records: [] }))
  templateOptions.value = (tpl.records || [])
    .filter((item: any) => item.templateCode)
    .map((item: any) => ({
      label: `${item.templateName} (${item.templateType})`,
      value: String(item.templateCode),
    }))
}

const loadPage = async () => {
  loading.value = true
  try {
    const res = await pageTaskCrawl(buildQuery(queryForm))
    setPageResult(res)
    res.records?.forEach((row: TaskWebCrawlConfig) => {
      if (row.sshEnabled === 1 && row.id != null) {
        loadTunnelStatus(row.id)
      }
    })
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  current.value = 1
  loadPage()
}

const handleReset = () => {
  queryForm.crawlName = ''
  queryForm.crawlCode = ''
  reset()
  loadPage()
}

const handleCreate = () => {
  formId.value = undefined
  formVisible.value = true
}

const handleEdit = (row: TaskWebCrawlConfig) => {
  formId.value = row.id
  formVisible.value = true
}

const handleDelete = async (row: TaskWebCrawlConfig) => {
  await ElMessageBox.confirm('确认删除该爬取配置？', '提示', {
    type: 'warning',
  })
  await deleteTaskCrawl(row.id!)
  ElMessage.success('删除成功')
  loadPage()
}

const handlePageChange = (c: number, s: number) => {
  current.value = c
  size.value = s
  loadPage()
}

const onFormSuccess = () => {
  formVisible.value = false
  loadPage()
}

onMounted(() => {
  loadOptions()
  loadPage()
})
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="爬取名称">
            <el-input v-model="queryForm.crawlName" placeholder="爬取名称" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="爬取编码">
            <el-input v-model="queryForm.crawlCode" placeholder="爬取编码" clearable />
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button type="primary" v-permission="'taskCrawl:create'" @click="handleCreate">新增爬取</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="crawlName" label="爬取名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="crawlCode" label="爬取编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="requestUrl" label="请求 URL" min-width="200" show-overflow-tooltip />
      <el-table-column prop="requestMethod" label="方法" width="80" />
      <el-table-column label="模板" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ templateOptions.find((t) => t.value === row.templateCode)?.label || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="outputFormat" label="输出格式" width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">启用</el-tag>
          <el-tag v-else type="danger">禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="SSH 隧道" width="160" v-permission="'taskCrawl:view'">
        <template #default="{ row }">
          <el-button
            v-if="row.sshEnabled === 1"
            link
            :type="tunnelMap[row.id!]?.connected ? 'danger' : 'success'"
            @click="handleToggleSshTunnel(row)"
          >
            {{ tunnelMap[row.id!]?.connected ? '关闭 SSH 隧道' : '开启 SSH 隧道' }}
          </el-button>
          <el-link
            v-if="tunnelMap[row.id!]?.connected && tunnelMap[row.id!]?.localUrl"
            type="primary"
            :underline="false"
            @click="openLocalUrl(row)"
          >
            本地地址
          </el-link>
          <span v-else-if="row.sshEnabled !== 1" class="text-muted">未启用</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'taskCrawl:edit'" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" v-permission="'taskCrawl:delete'" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <BasePagination :total="total" :current="current" :size="size" @change="handlePageChange" />

    <TaskCrawlForm
      v-model:visible="formVisible"
      :id="formId"
      :template-options="templateOptions"
      @success="onFormSuccess"
    />
  </div>
</template>
