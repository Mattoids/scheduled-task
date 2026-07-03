import { useUserStore } from '@/stores/user'
import type { Directive } from 'vue'

export const permission: Directive = {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    const codes = Array.isArray(value) ? value : [value]
    const has = codes.some((code) => userStore.hasPermission(code))
    if (!has && el.parentNode) {
      el.parentNode.removeChild(el)
    }
  },
}
