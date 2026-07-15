export interface SseOptions {
  url: string
  method?: 'GET' | 'POST'
  headers?: Record<string, string>
  onOpen?: () => void
  onMessage?: (event: string, data: unknown) => void
  onError?: (error: Error) => void
  onClose?: () => void
}

/**
 * 使用 fetch 实现的轻量级 SSE 客户端。
 * 相比原生 EventSource 可携带 Authorization 等自定义请求头。
 */
export function createSse(options: SseOptions): AbortController {
  const controller = new AbortController()

  fetch(options.url, {
    method: options.method || 'GET',
    headers: {
      Accept: 'text/event-stream',
      ...options.headers,
    },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        let msg = `SSE 请求失败: ${response.status}`
        try {
          const text = await response.text()
          if (text) msg += ` - ${text}`
        } catch {
          // ignore
        }
        throw new Error(msg)
      }
      if (!response.body) {
        throw new Error('SSE 响应没有 body')
      }
      options.onOpen?.()

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = 'message'

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const dataStr = line.slice(5).trim()
            let data: unknown = dataStr
            try {
              data = JSON.parse(dataStr)
            } catch {
              // 非 JSON 按原字符串透传
            }
            options.onMessage?.(currentEvent, data)
          } else if (line.trim() === '') {
            currentEvent = 'message'
          }
        }
      }
      options.onClose?.()
    })
    .catch((error) => {
      if (error.name !== 'AbortError') {
        options.onError?.(error)
      }
    })

  return controller
}
