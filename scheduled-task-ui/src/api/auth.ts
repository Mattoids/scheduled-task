import request from '@/utils/request'
import type { LoginRequest, LoginResponse, CurrentUserVo } from '@/types'

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export const login = (data: LoginRequest) => {
  return request.post<LoginResponse>('/auth/login', data)
}

export const getCurrentUser = () => {
  return request.get<CurrentUserVo>('/auth/me')
}

export const changePassword = (data: ChangePasswordRequest) => {
  return request.post<void>('/auth/change-password', data)
}
