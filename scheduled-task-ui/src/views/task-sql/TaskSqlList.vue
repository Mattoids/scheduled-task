<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import { pageTaskSql, deleteTaskSql } from '@/api/taskSql'
import { listDatasource } from '@/api/datasource'
import { listTemplate } from '@/api/template'
import TaskSqlForm from './TaskSqlForm.vue'
import type { TaskSqlConfig } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: 'SQL 管理' }])

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination()

const queryForm = reactive({
  sqlName: '',
  sqlCode: '',
  groupName: '',
})

const loading = ref(false)
const formVisible = ref(false)
const formId = ref<number | undefined>(undefined)

const datasourceOptions = ref<{ label: string; value: number }[]>([])
const templateOptions = ref<{ label: string; value: number }[]>([])
const groupOptions = ref<{ label: string; value: string }[]>([])

const loadOptions = async () => {
  const [ds, tpl] = await Promise.all([
    listDatasource({ size: 1000 }).catch(() => ({ records: [] })),
    listTemplate({ size: 1000 }).catch(() => ({ records: [] })),
  ])
  datasourceOptions.value = (ds.records || []).map((item: any) => ({
    label: item.name,
    value: item.id,
  }))
  templateOptions.value = (tpl.records || []).map((item: any) => ({
    label: `${item.templateName} (${item.templateType})`,
    value: item.id,
  }))
}

const refreshGroupOptions = () => {
  const groups = new Set<string>()
  records.value.forEach((item: TaskSqlConfig) => {
    if (item.groupName) {
      groups.add(item.groupName)
    }
  })
  groupOptions.value = Array.from(groups).map((g) => ({ label: g, value: g }))
}

const loadPage = async () => {
  loading.value = true
  try {
    const res = await pageTaskSql(buildQuery(queryForm))
    setPageResult(res)
    refreshGroupOptions()
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  current.value = 1
  loadPage()
}

const handleReset = () => {
  queryForm.sqlName = ''
  queryForm.sqlCode = ''
  queryForm.groupName = ''
  reset()
  loadPage()
}

const handleCreate = () => {
  formId.value = undefined
  formVisible.value = true
}

const handleEdit = (row: TaskSqlConfig) => {
  formId.value = row.id
  formVisible.value = true
}

const handleDelete = async (row: TaskSqlConfig) => {
  await ElMessageBox.confirm('确认删除该 SQL 配置？', '提示', { type: 'warning' })
  await deleteTaskSql(row.id!)
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
      <el-form-item label="SQL 名称">
        <el-input v-model="queryForm.sqlName" placeholder="SQL 名称" clearable />
      </el-form-item>
      <el-form-item label="SQL 编码">
        <el-input v-model="queryForm.sqlCode" placeholder="SQL 编码" clearable />
      </el-form-item>
      <el-form-item label="分组">
        <el-select v-model="queryForm.groupName" placeholder="全部分组" clearable style="width: 160px">
          <el-option
            v-for="item in groupOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button type="primary" v-permission="'task:create'" @click="handleCreate"
        >新增 SQL</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="sqlName" label="SQL 名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="sqlCode" label="SQL 编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="groupName" label="分组" min-width="120" show-overflow-tooltip />
      <el-table-column label="数据源" min-width="140">
        <template #default="{ row }">
          {{ datasourceOptions.find((d) => d.value === row.datasourceId)?.label || row.datasourceId }}
        </template>
      </el-table-column>
      <el-table-column label="模板" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ templateOptions.find((t) => t.value === row.templateId)?.label || '-' }}
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
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'task:edit'" @click="handleEdit(row)"
            >编辑</el-button>
          <el-button link type="danger" v-permission="'task:delete'" @click="handleDelete(row)"
            >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />

    <TaskSqlForm
      v-model:visible="formVisible"
      :id="formId"
      :datasource-options="datasourceOptions"
      :template-options="templateOptions"
      @success="onFormSuccess"
    />
  </div>
</template>
