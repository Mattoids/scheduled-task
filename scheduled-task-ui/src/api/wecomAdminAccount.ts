import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { WeComAdminAccount } from '@/types/entity'

export interface KeepAliveResult {
  success: boolean
  message: string
  lastKeepAliveTime?: string
}

export interface SsoTicketResult {
  ticket: string
  url: string
}

export const pageWeComAdminAccounts = (params: PageQuery & { keyword?: string }) => {
  return request.get<PageResult<WeComAdminAccount>>('/wecom-admin/accounts', { params })
}

/** 查询所有启用的账户（下拉选择用，不含 Cookie 原文） */
export const listEnabledWeComAdminAccounts = () => {
  return request.get<WeComAdminAccount[]>('/wecom-admin/accounts/enabled')
}

export const createWeComAdminAccount = (data: { accountName: string; adminCookie: string }) => {
  return request.post<WeComAdminAccount>('/wecom-admin/accounts', data)
}

export const updateWeComAdminAccount = (
  id: number,
  data: { accountName?: string; status?: number; keepAliveEnabled?: boolean; adminCookie?: string }
) => {
  return request.put<WeComAdminAccount>(`/wecom-admin/accounts/${id}`, data)
}

export const deleteWeComAdminAccount = (id: number) => {
  return request.delete(`/wecom-admin/accounts/${id}`)
}

export const triggerKeepAlive = (id: number) => {
  return request.post<KeepAliveResult>(`/wecom-admin/accounts/${id}/keep-alive`, null, { timeout: 60000 })
}

/** 打开已登录的企微管理后台（本机弹出浏览器窗口） */
export const openWeComAdmin = (id: number) => {
  return request.post<void>(`/wecom-admin/accounts/${id}/open-admin`, null, { timeout: 15000 })
}

/** 签发免登录跳转 ticket（返回代理入口 URL） */
export const issueSsoTicket = (id: number) => {
  return request.post<SsoTicketResult>(`/wecom-admin/accounts/${id}/sso-ticket`)
}

