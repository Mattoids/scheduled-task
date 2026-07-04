<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import {
  pageUser,
  createUser,
  updateUser,
  deleteUser,
  assignUserRoles,
  listRole,
} from "@/api/system";
import type { SysUser, SysRole } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "系统管理" }, { title: "用户管理" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({ username: "" });
const roles = ref<SysRole[]>([]);
const formVisible = ref(false);
const form = ref<Partial<SysUser>>({});
const formId = ref<number | undefined>(undefined);
const selectedRoleIds = ref<number[]>([]);

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageUser(buildQuery(queryForm));
    setPageResult(res);
  } finally {
    loading.value = false;
  }
};
const loadRoles = async () => {
  roles.value = await listRole();
};
const handleSearch = () => {
  current.value = 1;
  loadPage();
};
const handleReset = () => {
  queryForm.username = "";
  reset();
  loadPage();
};
const openForm = (row?: SysUser) => {
  formId.value = row?.id;
  form.value = row ? { ...row, password: "" } : { status: 1 };
  selectedRoleIds.value = [];
  formVisible.value = true;
};
const handleDelete = async (row: SysUser) => {
  await ElMessageBox.confirm("确认删除该用户？", "提示", { type: "warning" });
  await deleteUser(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};
const handleSubmit = async () => {
  if (!form.value.username) {
    ElMessage.warning("请输入用户名");
    return;
  }
  const data = { ...form.value };
  if (formId.value && !data.password) {
    delete data.password;
  }
  if (formId.value) {
    await updateUser(formId.value, data as SysUser);
    if (selectedRoleIds.value.length > 0) {
      await assignUserRoles(formId.value, selectedRoleIds.value);
    }
  } else {
    const res = await createUser(data as SysUser);
    // createUser returns void, cannot get id; role assignment skipped for new user in this simplified flow
    void res;
  }
  ElMessage.success(formId.value ? "修改成功" : "新增成功");
  formVisible.value = false;
  loadPage();
};
const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

onMounted(() => {
  loadRoles();
  loadPage();
});
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="用户名">
            <el-input v-model="queryForm.username" placeholder="用户名" clearable />
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button type="primary" v-permission="'system:user'" @click="openForm()"
        >新增用户</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="160" />
      <el-table-column prop="phone" label="手机号" min-width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
            row.status === 1 ? "启用" : "禁用"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-permission="'system:user'"
            @click="openForm(row)"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            v-permission="'system:user'"
            @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />

    <el-dialog
      v-model="formVisible"
      :title="formId ? '编辑用户' : '新增用户'"
      width="520px"
    >
      <el-form class="dialog-form" label-width="100px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="留空表示不修改"
            show-password
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="selectedRoleIds" multiple placeholder="选择角色">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
