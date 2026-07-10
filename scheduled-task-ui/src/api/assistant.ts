import request from '@/utils/request'
import type { AiAssistantService, AiAutoConfigResult } from '@/types'
import type { AiConversation } from '@/types/entity'

// AI 对话/生成涉及多次模型调用，30s 默认超时偏短，单独放宽到 3 分钟
const AI_REQUEST_TIMEOUT = 180000

export const chatWithAssistant = (data: {
  sessionId?: string
  datasourceId?: number
  message: string
}) => {
  return request.post<AiConversation>('/assistant/chat', data, { timeout: AI_REQUEST_TIMEOUT })
}

export const generateConfigByNaturalLanguage = (content: string) => {
  return request.post<AiAssistantService.NaturalConfigResult>('/assistant/generate-config', { content }, { timeout: AI_REQUEST_TIMEOUT })
}

export const autoConfigureByNaturalLanguage = (content: string) => {
  return request.post<AiAutoConfigResult>('/assistant/auto-configure', { content }, { timeout: AI_REQUEST_TIMEOUT })
}
