import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { StorageConfig } from '@/types/entity'

export const pageStorageConfig = (params: PageQuery) => {
  return request.get<PageResult<StorageConfig>>('/storage-config/page', { params })
}

export const listStorageConfig = () => {
  return request.get<StorageConfig[]>('/storage-config/list')
}

export const getStorageConfig = (id: number) => {
  return request.get<StorageConfig>(`/storage-config/${id}`)
}

export const createStorageConfig = (data: StorageConfig) => {
  return request.post<boolean>('/storage-config', data)
}

export const updateStorageConfig = (id: number, data: StorageConfig) => {
  return request.put<boolean>(`/storage-config/${id}`, data)
}

export const deleteStorageConfig = (id: number) => {
  return request.delete<boolean>(`/storage-config/${id}`)
}

export const testStorageConfig = (id: number) => {
  return request.post<string>(`/storage-config/${id}/test`)
}
