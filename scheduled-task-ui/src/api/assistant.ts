import request from '@/utils/request'
import type { AiAssistantService } from '@/types'
import type { AiConversation } from '@/types/entity'

export const chatWithAssistant = (data: {
  sessionId?: string
  datasourceId?: number
  message: string
}) => {
  return request.post<AiConversation>('/assistant/chat', data)
}

export const generateConfigByNaturalLanguage = (content: string) => {
  return request.post<AiAssistantService.NaturalConfigResult>('/assistant/generate-config', { content })
}
