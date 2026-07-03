import request from '@/utils/request'
import type { PageQuery, PageResult, TestConnectionResult } from '@/types'
import type { EmailConfig } from '@/types/entity'

export const pageEmailConfig = (params: PageQuery) => {
  return request.get<PageResult<EmailConfig>>('/email-config/page', { params })
}

export const listEmailConfig = (params?: PageQuery) => {
  return pageEmailConfig({ ...params, current: 1, size: 1000 })
}

export const getEmailConfig = (id: number) => {
  return request.get<EmailConfig>(`/email-config/${id}`)
}

export const createEmailConfig = (data: EmailConfig) => {
  return request.post<void>('/email-config', data)
}

export const updateEmailConfig = (id: number, data: EmailConfig) => {
  return request.put<void>(`/email-config/${id}`, data)
}

export const deleteEmailConfig = (id: number) => {
  return request.delete<void>(`/email-config/${id}`)
}

export const testEmailConfig = (data: EmailConfig) => {
  return request.post<TestConnectionResult>('/email-config/test', data)
}
