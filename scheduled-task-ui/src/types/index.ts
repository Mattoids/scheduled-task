export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  pages: number
  current: number
  size: number
  records: T[]
}

export interface PageQuery {
  current?: number
  size?: number
  [key: string]: any
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  nickname?: string
  userId?: number
  permissions: string[]
}

export interface CurrentUserVo {
  userId: number
  username: string
  nickname?: string
  permissions: string[]
}

export interface ChangeStatusRequest {
  status: string
}

export interface StageResult {
  stage: string
  success: boolean
  message?: string
}

export interface TestConnectionResult {
  success: boolean
  stage?: string
  message?: string
  stages?: StageResult[]
}

export interface DashboardStats {
  taskCount: number
  datasourceCount: number
  notificationConfigCount: number
  templateCount: number
  todayLogCount: number
  successLogCount: number
  failedLogCount: number
  taskStatusStats: Record<string, number>
  todayStatusStats: Record<string, number>
  recentLogs: RecentTaskLog[]
}

export interface RecentTaskLog {
  id: number
  taskId: number
  taskName: string
  triggerMode: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  startTime?: string
  endTime?: string
  resultMessage?: string
}

export namespace AiAssistantService {
  export interface NaturalConfigResult {
    type: string
    config: Record<string, any>
    summary: string
  }
}
