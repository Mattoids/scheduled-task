import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const breadcrumb = ref<{ title: string; path?: string }[]>([])

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const setBreadcrumb = (items: { title: string; path?: string }[]) => {
    breadcrumb.value = items
  }

  return {
    sidebarCollapsed,
    breadcrumb,
    toggleSidebar,
    setBreadcrumb,
  }
})
