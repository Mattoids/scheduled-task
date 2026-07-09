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

export interface CrawlSshTunnelInfo {
  connected: boolean
  localPort?: number
  localUrl?: string
  message?: string
}

export const openCrawlSshTunnel = (id: number) => {
  return request.post<CrawlSshTunnelInfo>(`/task-crawl/${id}/ssh-tunnel/open`)
}

export const closeCrawlSshTunnel = (id: number) => {
  return request.post<boolean>(`/task-crawl/${id}/ssh-tunnel/close`)
}

export const getCrawlSshTunnelStatus = (id: number) => {
  return request.get<CrawlSshTunnelInfo>(`/task-crawl/${id}/ssh-tunnel/status`)
}

export const previewRewriteTaskCrawl = (data: TaskWebCrawlConfig) => {
  return request.post<string>('/task-crawl/preview-rewrite', data, {
    responseType: 'text',
  })
}
