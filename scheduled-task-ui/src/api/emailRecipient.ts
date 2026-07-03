import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { EmailRecipient, EmailRecipientGroup } from '@/types/entity'

export const pageRecipient = (params: PageQuery) => {
  return request.get<PageResult<EmailRecipient>>('/email-recipient/page', { params })
}

export const listRecipient = (params?: PageQuery) => {
  return request.get<EmailRecipient[]>('/email-recipient/list', { params })
}

export const createRecipient = (data: EmailRecipient) => {
  return request.post<void>('/email-recipient', data)
}

export const updateRecipient = (id: number, data: EmailRecipient) => {
  return request.put<void>(`/email-recipient/${id}`, data)
}

export const deleteRecipient = (id: number) => {
  return request.delete<void>(`/email-recipient/${id}`)
}

export const listGroup = () => {
  return request.get<EmailRecipientGroup[]>('/email-recipient/group/list')
}

export const createGroup = (data: EmailRecipientGroup) => {
  return request.post<void>('/email-recipient/group', data)
}

export const updateGroup = (id: number, data: EmailRecipientGroup) => {
  return request.put<void>(`/email-recipient/group/${id}`, data)
}

export const deleteGroup = (id: number) => {
  return request.delete<void>(`/email-recipient/group/${id}`)
}
