import { useUserStore } from '@/stores/user'

export function usePermission() {
  const userStore = useUserStore()
  const has = (code: string | string[]) => userStore.hasPermission(code)
  return { has }
}
