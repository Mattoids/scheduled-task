import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getCurrentUser } from '@/api/auth'
import type { LoginRequest, LoginResponse, CurrentUserVo } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('scheduled-task-token') || '')
  const userInfo = ref<CurrentUserVo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const permissions = computed(() => userInfo.value?.permissions || [])

  const setToken = (value: string) => {
    token.value = value
    localStorage.setItem('scheduled-task-token', value)
  }

  const login = async (data: LoginRequest): Promise<LoginResponse> => {
    const res = await loginApi(data)
    setToken(res.token)
    userInfo.value = {
      userId: res.userId || 0,
      username: res.username,
      nickname: res.nickname,
      permissions: res.permissions,
    }
    return res
  }

  const fetchCurrentUser = async () => {
    if (!token.value) return
    const res = await getCurrentUser()
    userInfo.value = res
  }

  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('scheduled-task-token')
  }

  const hasPermission = (code: string | string[]) => {
    const codes = Array.isArray(code) ? code : [code]
    return codes.some((c) => permissions.value.includes(c))
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    permissions,
    login,
    fetchCurrentUser,
    logout,
    hasPermission,
  }
})
