import axios from 'axios'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
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

export const downloadTemplate = async (id: number, fileName?: string) => {
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const userStore = useUserStore()
  const res = await axios.get(`${baseURL}/template/${id}/download`, {
    responseType: 'blob',
    headers: {
      Authorization: `Bearer ${userStore.token}`,
    },
  })
  const blob = new Blob([res.data])
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || 'template'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
