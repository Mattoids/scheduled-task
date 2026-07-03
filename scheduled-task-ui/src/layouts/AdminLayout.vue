<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { menuRoutes } from '@/router/routes'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const activeMenu = computed(() => route.path)

const visibleMenuRoutes = computed(() => {
  return menuRoutes.filter((r) => {
    const perm = r.meta?.permission as string | null
    return !perm || userStore.hasPermission(perm)
  })
})

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const toggleSidebar = () => {
  appStore.toggleSidebar()
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
      <div class="logo">{{ appStore.sidebarCollapsed ? 'ST' : '定时任务报表' }}</div>
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        router
      >
        <template v-for="menu in visibleMenuRoutes" :key="menu.path">
          <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.path">
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
          <el-icon size="20" style="cursor: pointer" @click="toggleSidebar"
            >
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <span style="font-size: 16px; font-weight: 500">{{ route.meta?.title || '定时任务报表系统' }}</span>
        </div>
        <div class="header-right">
          <span>{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
          <el-dropdown @command="handleLogout">
            <span style="cursor: pointer">
              <el-icon><User /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
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
</template>
