import request from '@/utils/request'
import type { PageQuery, PageResult, TestConnectionResult } from '@/types'
import type { WeComBotConfig } from '@/types/entity'

export const pageWeComBotConfig = (params: PageQuery) => {
  return request.get<PageResult<WeComBotConfig>>('/wecom-bot-config/page', { params })
}

export const listWeComBotConfig = (params?: PageQuery) => {
  return pageWeComBotConfig({ ...params, current: 1, size: 1000 })
}

export const getWeComBotConfig = (id: number) => {
  return request.get<WeComBotConfig>(`/wecom-bot-config/${id}`)
}

export const createWeComBotConfig = (data: WeComBotConfig) => {
  return request.post<void>('/wecom-bot-config', data)
}

export const updateWeComBotConfig = (id: number, data: WeComBotConfig) => {
  return request.put<void>(`/wecom-bot-config/${id}`, data)
}

export const deleteWeComBotConfig = (id: number) => {
  return request.delete<void>(`/wecom-bot-config/${id}`)
}

export const testWeComBotConfig = (data: WeComBotConfig) => {
  return request.post<TestConnectionResult>('/wecom-bot-config/test', data)
}
