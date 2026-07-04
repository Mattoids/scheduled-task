import request from '@/utils/request'
import type { PageQuery, PageResult, TestConnectionResult } from '@/types'
import type { NotificationConfig } from '@/types/entity'

export const pageNotificationConfig = (params: PageQuery & { configName?: string; configType?: string }) => {
  return request.get<PageResult<NotificationConfig>>('/notification-config/page', { params })
}

export const listNotificationConfig = (params?: { configName?: string; configType?: string }) => {
  return pageNotificationConfig({ ...params, current: 1, size: 1000 })
}

export const getNotificationConfig = (id: number) => {
  return request.get<NotificationConfig>(`/notification-config/${id}`)
}

export const createNotificationConfig = (data: NotificationConfig) => {
  return request.post<void>('/notification-config', data)
}

export const updateNotificationConfig = (id: number, data: NotificationConfig) => {
  return request.put<void>(`/notification-config/${id}`, data)
}

export const deleteNotificationConfig = (id: number) => {
  return request.delete<void>(`/notification-config/${id}`)
}

export const testNotificationConfig = (data: NotificationConfig) => {
  return request.post<TestConnectionResult>('/notification-config/test', data)
}
