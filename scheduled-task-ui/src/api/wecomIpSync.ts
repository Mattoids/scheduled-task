import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { WeComIpSyncLog } from '@/types/entity'

export interface QrCodeResult {
  sessionId: string
  qrCodeBase64: string
  debug?: string
  pageTitle?: string
  pageUrl?: string
  debugScreenshot?: string
}

export interface LoginStatusResult {
  status: 'WAITING' | 'LOGGED_IN' | 'EXPIRED'
  cookie?: string
}

export interface SyncResult {
  success: boolean
  message: string
  skipped?: boolean
  currentIps?: string[]
  newIps?: string[]
}

export interface CookieCheckResult {
  valid: boolean
  message: string
}

export const generateQrCode = () => {
  return request.post<QrCodeResult>('/wecom-ip-sync/qr-code', null, { timeout: 60000 })
}

export const checkLoginStatus = (sessionId: string) => {
  return request.get<LoginStatusResult>(`/wecom-ip-sync/login-status/${sessionId}`)
}

export const triggerIpSync = (configId: number) => {
  return request.post<SyncResult>(`/wecom-ip-sync/trigger/${configId}`)
}

export const pageWeComIpSyncLogs = (params: PageQuery & { configId?: number; status?: string }) => {
  return request.get<PageResult<WeComIpSyncLog>>('/wecom-ip-sync/logs', { params })
}

export const checkCookieValid = (data: { configId?: number; agentId?: number; adminCookie?: string; appManageUrl?: string }) => {
  return request.post<CookieCheckResult>('/wecom-ip-sync/check-cookie', data, { timeout: 60000 })
}
