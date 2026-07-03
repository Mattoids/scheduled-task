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
