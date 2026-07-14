import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { checkDependencies, type DependencyItem } from '@/api/system'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const breadcrumb = ref<{ title: string; path?: string }[]>([])
  const dependencies = ref<DependencyItem[]>([])
  const dependenciesLoading = ref(false)

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const setBreadcrumb = (items: { title: string; path?: string }[]) => {
    breadcrumb.value = items
  }

  const chromiumAvailable = computed(() => {
    const item = dependencies.value.find((d) => d.key === 'chromium')
    return item?.available ?? null
  })

  const dependenciesReady = computed(() => {
    if (dependenciesLoading.value) return false
    if (dependencies.value.length === 0) return false
    return dependencies.value.every((d) => d.available)
  })

  const loadDependencies = async () => {
    if (dependenciesLoading.value) return
    dependenciesLoading.value = true
    try {
      dependencies.value = await checkDependencies()
    } catch {
      dependencies.value = []
    } finally {
      dependenciesLoading.value = false
    }
  }

  return {
    sidebarCollapsed,
    breadcrumb,
    dependencies,
    dependenciesLoading,
    chromiumAvailable,
    dependenciesReady,
    toggleSidebar,
    setBreadcrumb,
    loadDependencies,
  }
})
