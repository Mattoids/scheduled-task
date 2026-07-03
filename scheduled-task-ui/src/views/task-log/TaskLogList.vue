<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { usePagination } from '@/composables/usePagination'
import { pageTaskLog } from '@/api/task'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '任务日志' }])

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination()
const loading = ref(false)
const queryForm = reactive({
  status: '',
})

const loadPage = async () => {
  loading.value = true
  try {
    const res = await pageTaskLog(buildQuery(queryForm))
    setPageResult(res)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  current.value = 1
  loadPage()
}

const handleReset = () => {
  queryForm.status = ''
  reset()
  loadPage()
}

const statusType = (status?: string) => {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'warning'
    default:
      return 'info'
  }
}

const handlePageChange = (c: number, s: number) => {
  current.value = c
  size.value = s
  loadPage()
}

onMounted(loadPage)
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset"
      >
      <el-form-item label="状态">
        <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 140px"
          >
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="运行中" value="RUNNING" />
        </el-select>
      </el-form-item>
    </BaseSearchForm>

    <el-table v-loading="loading" :data="records" border stripe
      >
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="taskId" label="任务 ID" width="90" />
      <el-table-column prop="triggerMode" label="触发方式" width="100"
        >
        <template #default="{ row }">
          <el-tag :type="row.triggerMode === 'MANUAL' ? 'primary' : 'info'"
            >{{ row.triggerMode === 'MANUAL' ? '手动' : '自动' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100"
        >
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)"
            >
            {{ row.status === 'SUCCESS' ? '成功' : row.status === 'FAILED' ? '失败' : '运行中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column prop="endTime" label="结束时间" width="170" />
      <el-table-column prop="resultMessage" label="结果" min-width="160" show-overflow-tooltip />
      <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
      <el-table-column prop="filePath" label="文件路径" min-width="200" show-overflow-tooltip />
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />
  </div>
</template>
