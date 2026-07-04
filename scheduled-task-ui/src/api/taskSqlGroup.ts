import request from '@/utils/request'
import type { PageQuery, PageResult } from '@/types'
import type { TaskSqlGroup } from '@/types/entity'

export const listTaskSqlGroup = () => {
  return request.get<TaskSqlGroup[]>('/task-sql-group/list')
}

export const pageTaskSqlGroup = (params: PageQuery) => {
  return request.get<PageResult<TaskSqlGroup>>('/task-sql-group/page', { params })
}

export const getTaskSqlGroup = (id: number) => {
  return request.get<TaskSqlGroup>(`/task-sql-group/${id}`)
}

export const createTaskSqlGroup = (data: TaskSqlGroup) => {
  return request.post<void>('/task-sql-group', data)
}

export const updateTaskSqlGroup = (id: number, data: TaskSqlGroup) => {
  return request.put<void>(`/task-sql-group/${id}`, data)
}

export const deleteTaskSqlGroup = (id: number) => {
  return request.delete<void>(`/task-sql-group/${id}`)
}
