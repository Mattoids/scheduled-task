import request from '@/utils/request'
import type { PageQuery, PageResult, TestConnectionResult } from '@/types'
import type { WeComAppConfig } from '@/types/entity'

export const pageWeComAppConfig = (params: PageQuery) => {
  return request.get<PageResult<WeComAppConfig>>('/wecom-app-config/page', { params })
}

export const listWeComAppConfig = (params?: PageQuery) => {
  return pageWeComAppConfig({ ...params, current: 1, size: 1000 })
}

export const getWeComAppConfig = (id: number) => {
  return request.get<WeComAppConfig>(`/wecom-app-config/${id}`)
}

export const createWeComAppConfig = (data: WeComAppConfig) => {
  return request.post<void>('/wecom-app-config', data)
}

export const updateWeComAppConfig = (id: number, data: WeComAppConfig) => {
  return request.put<void>(`/wecom-app-config/${id}`, data)
}

export const deleteWeComAppConfig = (id: number) => {
  return request.delete<void>(`/wecom-app-config/${id}`)
}

export const testWeComAppConfig = (data: WeComAppConfig) => {
  return request.post<TestConnectionResult>('/wecom-app-config/test', data)
}
