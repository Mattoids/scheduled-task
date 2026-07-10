export interface AiMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}
