import request from '@/utils/request'
import type { DashboardStats } from '@/types'

export const getDashboardStats = () => {
  return request.get<DashboardStats>('/dashboard/stats')
}
