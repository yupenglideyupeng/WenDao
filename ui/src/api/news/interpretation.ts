import request from '@/utils/request'
import { getToken } from '@/utils/auth'

/** SSE 解读回调 */
export interface InterpretCallbacks {
  onChunk?: (text: string) => void
  onDone?: (data: { modelName?: string }) => void
  onError?: (msg: string) => void
}

/**
 * 发起一键解读 SSE 请求
 * 使用原生 fetch + ReadableStream，逐行解析流式响应
 * 返回 abort 函数，用于组件卸载时取消请求
 */
export function startInterpret(articleId: number, callbacks: InterpretCallbacks): () => void {
  const controller = new AbortController()
  const token = getToken()

  const url = `${import.meta.env.VITE_APP_BASE_API}/news/article/interpret/${articleId}?token=${token}`

  fetch(url, {
    method: 'GET',
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        callbacks.onError?.(`SSE连接失败，状态码：${response.status}`)
        return
      }

      const reader = response.body!.getReader()
      const decoder = new TextDecoder('utf-8')

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const chunk = decoder.decode(value, { stream: true })
        const lines = chunk.split('\n').filter((line) => line.trim() !== '')

        for (const line of lines) {
          if (line.startsWith('event:')) {
            continue
          }

          if (!line.startsWith('data:')) continue

          const dataStr = line.substring('data:'.length).trim()

          if (dataStr === '[DONE]') {
            continue
          }

          try {
            const parsed = JSON.parse(dataStr)

            if (parsed.modelName !== undefined && parsed.choices === undefined) {
              // 自定义 done 事件
              callbacks.onDone?.({ modelName: parsed.modelName })
            } else if (parsed.choices) {
              // 流式 chunk
              const content = parsed.choices?.[0]?.delta?.content
              if (content) {
                callbacks.onChunk?.(content)
              }
            }
          } catch {
            if (dataStr && !dataStr.startsWith('{')) {
              callbacks.onError?.(dataStr)
            }
          }
        }
      }
    })
    .catch((err: Error) => {
      if (err.name === 'AbortError') return
      callbacks.onError?.(err.message || 'SSE连接异常')
    })

  return () => controller.abort()
}
