<script setup lang="ts">
import { ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/stores/user";
import { useAppStore } from "@/stores/app";
import { menuRoutes } from "@/router/routes";
import { changePassword } from "@/api/auth";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const appStore = useAppStore();

const activeMenu = computed(() => route.path);

const visibleMenuRoutes = computed(() => {
  return menuRoutes.filter((r) => {
    const perm = r.meta?.permission as string | null;
    return !perm || userStore.hasPermission(perm);
  });
});

const passwordDialogVisible = ref(false);
const passwordFormRef = ref();
const passwordForm = ref({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});
const passwordLoading = ref(false);

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error("请再次输入新密码"));
  } else if (value !== passwordForm.value.newPassword) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const passwordRules = {
  oldPassword: [{ required: true, message: "请输入原密码", trigger: "blur" }],
  newPassword: [{ required: true, message: "请输入新密码", trigger: "blur" }],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: "blur" },
  ],
};

const handleCommand = (command: string) => {
  if (command === "logout") {
    userStore.logout();
    router.push("/login");
  } else if (command === "changePassword") {
    passwordDialogVisible.value = true;
  }
};

const resetPasswordForm = () => {
  passwordForm.value = {
    oldPassword: "",
    newPassword: "",
    confirmPassword: "",
  };
};

const handlePasswordSubmit = async () => {
  const valid = await passwordFormRef.value
    ?.validate()
    .catch(() => false);
  if (!valid) return;

  passwordLoading.value = true;
  try {
    await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    });
    ElMessage.success("密码修改成功");
    passwordDialogVisible.value = false;
    resetPasswordForm();
  } finally {
    passwordLoading.value = false;
  }
};

const toggleSidebar = () => {
  appStore.toggleSidebar();
};
</script>

<template>
  <div class="admin-layout">
    <aside
      class="admin-sidebar"
      :class="{ collapsed: appStore.sidebarCollapsed }"
    >
      <div class="logo">
        {{ appStore.sidebarCollapsed ? "ST" : "定时任务报表" }}
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        router
      >
        <template v-for="menu in visibleMenuRoutes" :key="menu.path">
          <el-sub-menu
            v-if="menu.children && menu.children.length"
            :index="menu.path"
          >
            <template #title>
              <el-icon v-if="menu.meta?.icon">
                <component :is="menu.meta.icon" />
              </el-icon>
              <span>{{ menu.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.path"
              :index="child.path"
              :route="child.path"
            >
              {{ child.meta?.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path" :route="menu.path">
            <el-icon v-if="menu.meta?.icon">
              <component :is="menu.meta.icon" />
            </el-icon>
            <span>{{ menu.meta?.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>

    <div class="admin-main">
      <header class="admin-header">
        <div class="header-left">
          <el-icon size="20" style="cursor: pointer" @click="toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <span style="font-size: 16px; font-weight: 500">{{
            route.meta?.title || "定时任务报表系统"
          }}</span>
        </div>
        <div class="header-right">
          <span>{{
            userStore.userInfo?.nickname || userStore.userInfo?.username
          }}</span>
          <el-dropdown @command="handleCommand">
            <span style="cursor: pointer">
              <el-icon><User /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="changePassword"
                  >修改密码</el-dropdown-item
                >
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="admin-content">
        <RouterView />
      </main>
    </div>
  </div>

  <el-dialog
    v-model="passwordDialogVisible"
    title="修改密码"
    width="420px"
    @close="resetPasswordForm"
  >
    <el-form
      ref="passwordFormRef"
      :model="passwordForm"
      :rules="passwordRules"
      class="dialog-form"
      label-width="90px"
    >
      <el-form-item label="原密码" prop="oldPassword">
        <el-input
          v-model="passwordForm.oldPassword"
          type="password"
          placeholder="请输入原密码"
          show-password
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="passwordForm.newPassword"
          type="password"
          placeholder="请输入新密码"
          show-password
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="passwordForm.confirmPassword"
          type="password"
          placeholder="请再次输入新密码"
          show-password
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordDialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="passwordLoading"
        @click="handlePasswordSubmit"
        >确定</el-button
      >
    </template>
  </el-dialog>
</template>
