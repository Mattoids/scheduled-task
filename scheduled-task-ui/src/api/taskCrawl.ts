import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { TaskWebCrawlConfig } from '@/types/entity'

export interface WebCrawlPreviewResult {
  success: boolean
  statusCode?: number
  message?: string
  title?: string
  content?: string
}

export const pageTaskCrawl = (params: PageQuery & { crawlName?: string; crawlCode?: string }) => {
  return request.get<PageResult<TaskWebCrawlConfig>>('/task-crawl/page', { params })
}

export const listTaskCrawl = () => {
  return request.get<TaskWebCrawlConfig[]>('/task-crawl/list')
}

export const getTaskCrawl = (id: number) => {
  return request.get<TaskWebCrawlConfig>(`/task-crawl/${id}`)
}

export const createTaskCrawl = (data: TaskWebCrawlConfig) => {
  return request.post<void>('/task-crawl', data)
}

export const updateTaskCrawl = (id: number, data: TaskWebCrawlConfig) => {
  return request.put<void>(`/task-crawl/${id}`, data)
}

export const deleteTaskCrawl = (id: number) => {
  return request.delete<void>(`/task-crawl/${id}`)
}

export const previewTaskCrawl = (data: TaskWebCrawlConfig) => {
  return request.post<WebCrawlPreviewResult>('/task-crawl/preview', data)
}

export const previewRewriteTaskCrawl = (data: TaskWebCrawlConfig) => {
  return request.post<string>('/task-crawl/preview-rewrite', data, {
    responseType: 'text',
  })
}
