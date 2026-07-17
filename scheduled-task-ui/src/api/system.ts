import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { SysUser, SysRole, SysPermission } from '@/types/entity'

export const pageUser = (params: PageQuery) => {
  return request.get<PageResult<SysUser>>('/system/user/page', { params })
}

export const createUser = (data: SysUser) => {
  return request.post<void>('/system/user', data)
}

export const updateUser = (id: number, data: SysUser) => {
  return request.put<void>(`/system/user/${id}`, data)
}

export const deleteUser = (id: number) => {
  return request.delete<void>(`/system/user/${id}`)
}

export const assignUserRoles = (userId: number, roleIds: number[]) => {
  return request.post<void>(`/system/user/${userId}/roles`, roleIds)
}

export const listRole = () => {
  return request.get<SysRole[]>('/system/role/list')
}

export const createRole = (data: SysRole) => {
  return request.post<void>('/system/role', data)
}

export const updateRole = (id: number, data: SysRole) => {
  return request.put<void>(`/system/role/${id}`, data)
}

export const deleteRole = (id: number) => {
  return request.delete<void>(`/system/role/${id}`)
}

export interface ChromiumStatusResult {
  available: boolean
  message: string
}

export const checkChromium = () => {
  return request.get<ChromiumStatusResult>('/system/chromium')
}

export interface DependencyItem {
  key: string
  name: string
  available: boolean
  installable: boolean
  message: string
}

export const checkDependencies = () => {
  return request.get<DependencyItem[]>('/system/dependencies')
}

import { createSse } from '@/utils/sse'

export interface InstallProgressEvent {
  phase?: string
  message?: string
  percentage?: number
  level?: 'info' | 'warn' | 'error'
}

export interface InstallCompleteEvent {
  success: boolean
  message?: string
  dependencies?: DependencyItem[]
}

export interface InstallSseHandlers {
  onOpen?: () => void
  onMessage?: (event: string, data: unknown) => void
  onError?: (error: Error) => void
  onClose?: () => void
}

export const installDependency = (key: string, token: string, handlers: InstallSseHandlers) => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const url = `${baseUrl}/system/dependencies/${encodeURIComponent(key)}/install`
  return createSse({
    url,
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    ...handlers,
  })
}

export interface InstallProgressSnapshot {
  key: string
  phase: string
  percentage: number
  status: string
  message: string
  running: boolean
  logs: string[]
}

export const getInstallProgress = (key: string) => {
  return request.get<InstallProgressSnapshot>(`/system/dependencies/${encodeURIComponent(key)}/install/progress`)
}

export const getInstallStatus = (key: string) => {
  return request.get<{ installing: boolean }>(`/system/dependencies/${encodeURIComponent(key)}/install/status`)
}

export const listPermission = () => {
  return request.get<SysPermission[]>('/system/permission/list')
}
