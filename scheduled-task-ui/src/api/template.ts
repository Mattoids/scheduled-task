import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { ReportTemplate } from '@/types/entity'

export const pageTemplate = (params: PageQuery) => {
  return request.get<PageResult<ReportTemplate>>('/template/page', { params })
}

export const listTemplate = (params?: PageQuery) => {
  return pageTemplate({ ...params, current: 1, size: 1000 })
}

export const getTemplate = (id: number) => {
  return request.get<ReportTemplate>(`/template/${id}`)
}

export const uploadTemplate = (data: FormData) => {
  return request.post<void>('/template/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const updateTemplate = (id: number, data: Partial<ReportTemplate>) => {
  return request.put<void>(`/template/${id}`, data)
}

export const deleteTemplate = (id: number) => {
  return request.delete<void>(`/template/${id}`)
}
