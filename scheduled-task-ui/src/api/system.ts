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

export const listPermission = () => {
  return request.get<SysPermission[]>('/system/permission/list')
}
