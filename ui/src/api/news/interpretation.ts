import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import type { NewsInterpretation } from '@/types/api/news/interpretation'
import type { AjaxResult } from '@/types'

/** 查询文章最新解读记录 */
export function getLatestInterpretation(articleId: number): Promise<AjaxResult<NewsInterpretation>> {
  return request({ url: `/news/article/interpret/${articleId}/latest`, method: 'get' })
}

/** 查询文章所有历史解读记录 */
export function getInterpretationList(articleId: number): Promise<AjaxResult<NewsInterpretation[]>> {
  return request({ url: `/news/article/interpret/${articleId}/list`, method: 'get' })
}

/** SSE 解读回调 */
export interface InterpretCallbacks {
  onStart?: (data: { recordId: number; interpretCount: number }) => void
  onChunk?: (text: string) => void
  onDone?: (data: { modelName?: string }) => void
  onError?: (msg: string) => void
}

/**
 * 发起一键解读 SSE 请求
 * 使用原生 fetch + ReadableStream，逐行解析 DeepSeek 流式响应
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
        // 按行分割，过滤空行
        const lines = chunk.split('\n').filter((line) => line.trim() !== '')

        for (const line of lines) {
          // 处理自定义事件行：event: xxx
          // 处理数据行：data: xxx
          if (line.startsWith('event:')) {
            // 记录当前事件类型（通过下一个 data: 行处理）
            continue
          }

          if (!line.startsWith('data:')) continue

          const dataStr = line.substring('data:'.length).trim()

          if (dataStr === '[DONE]') {
            // OpenAI 流结束信号，跳过此行继续处理（自定义 done 事件可能在同一 chunk 中）
            continue
          }

          // 尝试解析 JSON
          try {
            const parsed = JSON.parse(dataStr)

            // 判断是自定义事件数据还是 DeepSeek 原始 chunk
            if (parsed.recordId !== undefined) {
              // 自定义 start 事件
              callbacks.onStart?.({
                recordId: parsed.recordId,
                interpretCount: parsed.interpretCount
              })
            } else if (parsed.modelName !== undefined && parsed.choices === undefined) {
              // 自定义 done 事件
              callbacks.onDone?.({ modelName: parsed.modelName })
            } else if (parsed.choices) {
              // DeepSeek 原始流式 chunk
              const content = parsed.choices?.[0]?.delta?.content
              if (content) {
                callbacks.onChunk?.(content)
              }
            }
          } catch {
            // 非 JSON 内容（如纯文本错误信息）当做错误处理
            if (dataStr && !dataStr.startsWith('{')) {
              callbacks.onError?.(dataStr)
            }
          }
        }
      }
    })
    .catch((err: Error) => {
      if (err.name === 'AbortError') return // 主动取消，不触发错误回调
      callbacks.onError?.(err.message || 'SSE连接异常')
    })

  return () => controller.abort()
}
