import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { AiConfig } from '@/types/entity'

export const pageAiConfig = (params: PageQuery) => {
  return request.get<PageResult<AiConfig>>('/ai-config/page', { params })
}

export const listAiConfig = (params?: PageQuery) => {
  return pageAiConfig({ ...params, current: 1, size: 1000 })
}

export const getAiConfig = (id: number) => {
  return request.get<AiConfig>(`/ai-config/${id}`)
}

export const createAiConfig = (data: AiConfig) => {
  return request.post<void>('/ai-config', data)
}

export const updateAiConfig = (id: number, data: AiConfig) => {
  return request.put<void>(`/ai-config/${id}`, data)
}

export const deleteAiConfig = (id: number) => {
  return request.delete<void>(`/ai-config/${id}`)
}

export const testAiConfig = (id: number) => {
  return request.post<string>(`/ai-config/${id}/test`)
}

export const testAiConfigData = (data: AiConfig) => {
  return request.post<string>('/ai-config/test', data)
}
