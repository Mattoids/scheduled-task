import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { TaskSqlConfig } from '@/types/entity'

export const pageTaskSql = (params: PageQuery) => {
  return request.get<PageResult<TaskSqlConfig>>('/task-sql/page', { params })
}

export const listTaskSql = () => {
  return request.get<TaskSqlConfig[]>('/task-sql/list')
}

export const getTaskSql = (id: number) => {
  return request.get<TaskSqlConfig>(`/task-sql/${id}`)
}

export const createTaskSql = (data: TaskSqlConfig) => {
  return request.post<void>('/task-sql', data)
}

export const updateTaskSql = (id: number, data: TaskSqlConfig) => {
  return request.put<void>(`/task-sql/${id}`, data)
}

export const deleteTaskSql = (id: number) => {
  return request.delete<void>(`/task-sql/${id}`)
}
