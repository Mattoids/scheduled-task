<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRole, createRole, updateRole, deleteRole, listPermission } from '@/api/system'
import type { SysRole, SysPermission } from '@/types/entity'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.setBreadcrumb([{ title: '系统管理' }, { title: '角色管理' }])

const roles = ref<SysRole[]>([])
const permissions = ref<SysPermission[]>([])
const loading = ref(false)
const formVisible = ref(false)
const form = ref<Partial<SysRole>>({})
const formId = ref<number | undefined>(undefined)
const selectedPermissions = ref<number[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const [roleRes, permRes] = await Promise.all([listRole(), listPermission()])
    roles.value = roleRes
    permissions.value = permRes
  } finally {
    loading.value = false
  }
}

const openForm = (row?: SysRole) => {
  formId.value = row?.id
  form.value = row ? { ...row } : { status: 1 }
  selectedPermissions.value = []
  formVisible.value = true
}

const handleDelete = async (row: SysRole) => {
  await ElMessageBox.confirm('确认删除该角色？', '提示', { type: 'warning' })
  await deleteRole(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

const handleSubmit = async () => {
  if (!form.value.roleCode || !form.value.roleName) {
    ElMessage.warning('请输入角色编码和名称')
    return
  }
  if (formId.value) {
    await updateRole(formId.value, form.value as SysRole)
  } else {
    await createRole(form.value as SysRole)
  }
  ElMessage.success(formId.value ? '修改成功' : '新增成功')
  formVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-card"
    >
    <div class="table-toolbar"
      >
      <el-button type="primary" v-permission="'system:role'" @click="openForm()">新增角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roles" border stripe
      >
      <el-table-column prop="roleCode" label="角色编码" min-width="140" />
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
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
            <el-button link type="primary" v-permission="'system:role'" @click="openForm(row)"
              >编辑</el-button>
            <el-button link type="danger" v-permission="'system:role'" @click="handleDelete(row)"
              >删除</el-button>
          </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="formId ? '编辑角色' : '新增角色'" width="560px"
      >
      <el-form label-width="80px"
        >
        <el-form-item label="角色编码"
          >
          <el-input v-model="form.roleCode" placeholder="角色编码" />
        </el-form-item>
        <el-form-item label="角色名称"
          >
          <el-input v-model="form.roleName" placeholder="角色名称" />
        </el-form-item>
        <el-form-item label="描述"
          >
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="描述" />
        </el-form-item>
        <el-form-item label="权限"
          >
          <el-select v-model="selectedPermissions" multiple placeholder="选择权限" style="width: 100%"
            >
              <el-option
                v-for="perm in permissions"
                :key="perm.id"
                :label="`${perm.permissionName} (${perm.permissionCode})`"
                :value="perm.id!"
              />
            </el-select>
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
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
