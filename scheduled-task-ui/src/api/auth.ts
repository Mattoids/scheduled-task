import request from '@/utils/request'
import type { LoginRequest, LoginResponse, CurrentUserVo } from '@/types'

export const login = (data: LoginRequest) => {
  return request.post<LoginResponse>('/auth/login', data)
}

export const getCurrentUser = () => {
  return request.get<CurrentUserVo>('/auth/me')
}
