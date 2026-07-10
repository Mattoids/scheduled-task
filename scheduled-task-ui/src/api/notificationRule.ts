import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { NotificationRule } from '@/types/entity'

export const listNotificationRule = (eventType?: string) => {
  return request.get<NotificationRule[]>('/notification-rule/list', { params: { eventType } })
}

export const pageNotificationRule = (params: PageQuery & { eventType?: string; channel?: string; taskCode?: string }) => {
  return request.get<PageResult<NotificationRule>>('/notification-rule/page', { params })
}

export const createNotificationRule = (data: NotificationRule) => {
  return request.post<void>('/notification-rule', data)
}

export const updateNotificationRule = (id: number, data: NotificationRule) => {
  return request.put<void>(`/notification-rule/${id}`, data)
}

export const updateNotificationRuleEnabled = (id: number, enabled: number) => {
  return request.put<void>(`/notification-rule/${id}/enabled`, { enabled })
}

export const deleteNotificationRule = (id: number) => {
  return request.delete<void>(`/notification-rule/${id}`)
}
