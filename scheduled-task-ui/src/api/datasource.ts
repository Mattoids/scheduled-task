import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { DatasourceConfig } from '@/types/entity'
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
