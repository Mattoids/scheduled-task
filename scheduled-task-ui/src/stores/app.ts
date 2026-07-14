import { defineStore } from 'pinia'
import { ref } from 'vue'
import { checkChromium } from '@/api/system'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const breadcrumb = ref<{ title: string; path?: string }[]>([])
  const chromiumAvailable = ref<boolean | null>(null)
  const chromiumLoading = ref(false)

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const setBreadcrumb = (items: { title: string; path?: string }[]) => {
    breadcrumb.value = items
  }

  const loadChromiumStatus = async () => {
    if (chromiumLoading.value) return
    chromiumLoading.value = true
    try {
      const res = await checkChromium()
      chromiumAvailable.value = res.available
    } catch {
      chromiumAvailable.value = false
    } finally {
      chromiumLoading.value = false
    }
  }

  return {
    sidebarCollapsed,
    breadcrumb,
    chromiumAvailable,
    chromiumLoading,
    toggleSidebar,
    setBreadcrumb,
    loadChromiumStatus,
  }
})
