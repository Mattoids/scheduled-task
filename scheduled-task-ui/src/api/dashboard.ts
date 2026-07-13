import request from '@/utils/request'
import type { DashboardStats } from '@/types'

export const getDashboardStats = () => {
  return request.get<DashboardStats>('/dashboard/stats')
}

export interface ServerTimeResponse {
  /** 服务器 Unix 毫秒时间戳（用于 RTT/2 延迟补偿） */
  serverTimeMillis: number
  /** IANA 时区 ID，如 Asia/Shanghai */
  timeZone: string
  /** UTC 偏移，如 +08:00 */
  utcOffset: string
}

export const getServerTime = () => {
  return request.get<ServerTimeResponse>('/dashboard/server-time')
}
