<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import {
  pageRecipient,
  createRecipient,
  updateRecipient,
  deleteRecipient,
  listGroup,
  createGroup,
  updateGroup,
  deleteGroup,
} from '@/api/emailRecipient'
import type { EmailRecipient, EmailRecipientGroup } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '收件人管理' }])

const activeTab = ref('recipient')

// Recipients
const { current, size, total, records, buildQuery, setPageResult, reset } = usePagination()
const recipientLoading = ref(false)
const recipientQuery = reactive({ recipientName: '', groupId: undefined as number | undefined })
const groupOptions = ref<EmailRecipientGroup[]>([])
const recipientFormVisible = ref(false)
const recipientForm = ref<Partial<EmailRecipient>>({})
const recipientFormId = ref<number | undefined>(undefined)

const loadRecipients = async () => {
  recipientLoading.value = true
  try {
    const res = await pageRecipient(buildQuery(recipientQuery))
    setPageResult(res)
  } finally {
    recipientLoading.value = false
  }
}

const loadGroups = async () => {
  groupOptions.value = await listGroup()
}

const handleRecipientSearch = () => {
  current.value = 1
  loadRecipients()
}
const handleRecipientReset = () => {
  recipientQuery.recipientName = ''
  recipientQuery.groupId = undefined
  reset()
  loadRecipients()
}
const openRecipientForm = (row?: EmailRecipient) => {
  recipientFormId.value = row?.id
  recipientForm.value = row
    ? { ...row }
    : { email: '', recipientName: '', groupId: undefined, status: 1 }
  recipientFormVisible.value = true
}
const saveRecipient = async () => {
  if (!recipientForm.value.email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  if (recipientFormId.value) {
    await updateRecipient(recipientFormId.value, recipientForm.value as EmailRecipient)
  } else {
    await createRecipient(recipientForm.value as EmailRecipient)
  }
  ElMessage.success('保存成功')
  recipientFormVisible.value = false
  loadRecipients()
}
const handleDeleteRecipient = async (row: EmailRecipient) => {
  await ElMessageBox.confirm('确认删除该收件人？', '提示', { type: 'warning' })
  await deleteRecipient(row.id!)
  ElMessage.success('删除成功')
  loadRecipients()
}

// Groups
const groupFormVisible = ref(false)
const groupForm = ref<Partial<EmailRecipientGroup>>({})
const groupFormId = ref<number | undefined>(undefined)

const openGroupForm = (row?: EmailRecipientGroup) => {
  groupFormId.value = row?.id
  groupForm.value = row ? { ...row } : { groupName: '', groupCode: '', description: '', status: 1 }
  groupFormVisible.value = true
}
const saveGroup = async () => {
  if (!groupForm.value.groupName || !groupForm.value.groupCode) {
    ElMessage.warning('请输入组名和组编码')
    return
  }
  if (groupFormId.value) {
    await updateGroup(groupFormId.value, groupForm.value as EmailRecipientGroup)
  } else {
    await createGroup(groupForm.value as EmailRecipientGroup)
  }
  ElMessage.success('保存成功')
  groupFormVisible.value = false
  loadGroups()
}
const handleDeleteGroup = async (row: EmailRecipientGroup) => {
  await ElMessageBox.confirm('确认删除该收件组？', '提示', { type: 'warning' })
  await deleteGroup(row.id!)
  ElMessage.success('删除成功')
  loadGroups()
}

const handleRecipientPageChange = (c: number, s: number) => {
  current.value = c
  size.value = s
  loadRecipients()
}

onMounted(() => {
  loadGroups()
  loadRecipients()
})
</script>

<template>
  <div class="page-card"
    >
    <el-tabs v-model="activeTab"
      >
      <el-tab-pane label="收件人" name="recipient"
        >
        <BaseSearchForm @search="handleRecipientSearch" @reset="handleRecipientReset"
          >
          <el-form-item label="收件人名称"
            >
            <el-input v-model="recipientQuery.recipientName" placeholder="收件人名称" clearable />
          </el-form-item>
          <el-form-item label="所属分组"
            >
            <el-select v-model="recipientQuery.groupId" placeholder="全部" clearable style="width: 160px"
              >
              <el-option
                v-for="g in groupOptions"
                :key="g.id"
                :label="g.groupName"
                :value="g.id!"
              />
            </el-select>
          </el-form-item>
        </BaseSearchForm>

        <div class="table-toolbar"
          >
          <el-button type="primary" v-permission="'email:create'" @click="openRecipientForm()"
            >新增收件人</el-button>
        </div>

        <el-table v-loading="recipientLoading" :data="records" border stripe
          >
          <el-table-column prop="recipientName" label="收件人名称" min-width="140" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="分组" min-width="140"
            >
            <template #default="{ row }"
              >
              {{ groupOptions.find((g) => g.id === row.groupId)?.groupName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90"
            >
            <template #default="{ row }"
              >
                <el-tag :type="row.status === 1 ? 'success' : 'danger'"
                  >{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right"
            >
            <template #default="{ row }"
              >
              <el-button link type="primary" v-permission="'email:edit'" @click="openRecipientForm(row)"
                >编辑</el-button>
              <el-button link type="danger" v-permission="'email:delete'" @click="handleDeleteRecipient(row)"
                >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <BasePagination
          :total="total"
          :current="current"
          :size="size"
          @change="handleRecipientPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="收件组" name="group"
        >
        <div class="table-toolbar"
          >
          <el-button type="primary" v-permission="'email:create'" @click="openGroupForm()"
            >新增收件组</el-button>
        </div>

        <el-table :data="groupOptions" border stripe
          >
          <el-table-column prop="groupName" label="组名" min-width="140" />
          <el-table-column prop="groupCode" label="组编码" min-width="140" />
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90"
            >
            <template #default="{ row }"
              >
                <el-tag :type="row.status === 1 ? 'success' : 'danger'"
                  >{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right"
            >
            <template #default="{ row }"
              >
              <el-button link type="primary" v-permission="'email:edit'" @click="openGroupForm(row)"
                >编辑</el-button>
              <el-button link type="danger" v-permission="'email:delete'" @click="handleDeleteGroup(row)"
                >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- Recipient Form -->
    <el-dialog v-model="recipientFormVisible" :title="recipientFormId ? '编辑收件人' : '新增收件人'" width="500px"
      >
      <el-form label-width="90px"
        >
        <el-form-item label="收件人名称"
          >
          <el-input v-model="recipientForm.recipientName" placeholder="收件人名称" />
        </el-form-item>
        <el-form-item label="邮箱"
          >
          <el-input v-model="recipientForm.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="所属分组"
          >
          <el-select v-model="recipientForm.groupId" placeholder="请选择" clearable style="width: 100%"
            >
              <el-option
                v-for="g in groupOptions"
                :key="g.id"
                :label="g.groupName"
                :value="g.id!"
              />
            </el-select>
        </el-form-item>
        <el-form-item label="状态"
          >
          <el-radio-group v-model="recipientForm.status"
            >
              <el-radio :label="1">启用</el-radio>
              <el-radio :label="0">禁用</el-radio>
            </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recipientFormVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRecipient">确定</el-button>
      </template>
    </el-dialog>

    <!-- Group Form -->
    <el-dialog v-model="groupFormVisible" :title="groupFormId ? '编辑收件组' : '新增收件组'" width="500px"
      >
      <el-form label-width="90px"
        >
        <el-form-item label="组名"
          >
          <el-input v-model="groupForm.groupName" placeholder="组名" />
        </el-form-item>
        <el-form-item label="组编码"
          >
          <el-input v-model="groupForm.groupCode" placeholder="组编码" />
        </el-form-item>
        <el-form-item label="描述"
          >
          <el-input v-model="groupForm.description" type="textarea" :rows="2" placeholder="描述" />
        </el-form-item>
        <el-form-item label="状态"
          >
          <el-radio-group v-model="groupForm.status"
            >
              <el-radio :label="1">启用</el-radio>
              <el-radio :label="0">禁用</el-radio>
            </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupFormVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
