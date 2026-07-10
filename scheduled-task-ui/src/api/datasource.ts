import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { AiKnowledgeDoc, DatasourceConfig, DatasourceSchemaSyncLog } from '@/types/entity'
import type { TestConnectionResult } from '@/types'

export const pageDatasource = (params: PageQuery) => {
  return request.get<PageResult<DatasourceConfig>>('/datasource/page', { params })
}

export const listDatasource = (params?: PageQuery) => {
  return pageDatasource({ ...params, current: 1, size: 1000 })
}

export const getDatasource = (id: number) => {
  return request.get<DatasourceConfig>(`/datasource/${id}`)
}

export const createDatasource = (data: DatasourceConfig) => {
  return request.post<void>('/datasource', data)
}

export const updateDatasource = (id: number, data: DatasourceConfig) => {
  return request.put<void>(`/datasource/${id}`, data)
}

export const deleteDatasource = (id: number) => {
  return request.delete<void>(`/datasource/${id}`)
}

export const testDatasource = (id: number) => {
  return request.post<TestConnectionResult>(`/datasource/${id}/test`)
}

export const testDatasourceConfig = (data: DatasourceConfig) => {
  return request.post<TestConnectionResult>('/datasource/test', data)
}

export const syncDatasourceSchema = (id: number) => {
  // 同步可能耗时较久（元数据采集 + AI 生成数据字典），单独放宽到 10 分钟
  return request.post<AiKnowledgeDoc>(`/datasource/${id}/sync-schema`, null, { timeout: 600000 })
}

export const pageDatasourceSyncLogs = (id: number, params: PageQuery) => {
  return request.get<PageResult<DatasourceSchemaSyncLog>>(`/datasource/${id}/sync-logs`, { params })
}

export const getSchemaDocContent = (id: number, docId: number) => {
  return request.get<string>(`/datasource/${id}/schema-docs/${docId}/content`)
}
