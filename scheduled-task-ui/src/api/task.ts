import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { TaskConfig, TaskLog } from '@/types/entity'
import type { ChangeStatusRequest } from '@/types'

export const pageTask = (params: PageQuery) => {
  return request.get<PageResult<TaskConfig>>('/task/page', { params })
}

export const getTask = (id: number) => {
  return request.get<TaskConfig>(`/task/${id}`)
}

export const createTask = (data: TaskConfig) => {
  return request.post<void>('/task', data)
}

export const updateTask = (id: number, data: TaskConfig) => {
  return request.put<void>(`/task/${id}`, data)
}

export const updateTaskStatus = (id: number, status: string) => {
  return request.put<void>(`/task/${id}/status`, { status } as ChangeStatusRequest)
}

export const deleteTask = (id: number) => {
  return request.delete<void>(`/task/${id}`)
}

export const triggerTask = (id: number) => {
  return request.post<void>(`/task/${id}/trigger`)
}

export const listTaskLogs = (taskId: number, params: PageQuery) => {
  return request.get<PageResult<TaskLog>>(`/task/${taskId}/logs`, { params })
}

export const pageTaskLog = (params: PageQuery) => {
  return request.get<PageResult<TaskLog>>('/task-log/page', { params })
}
