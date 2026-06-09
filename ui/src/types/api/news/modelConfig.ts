import type { BaseEntity } from '@/types/api/common'

/** 模型配置查询参数 */
export interface NewsModelConfigQueryParams {
  pageNum?: number
  pageSize?: number
  name?: string
  provider?: string
  modelName?: string
  isActive?: number
}

/** 模型配置实体 */
export interface NewsModelConfig extends BaseEntity {
  id?: number
  name: string
  provider: string         // DEEPSEEK/SILICONFLOW/BAILIAN/ZHIPU/VOLCENGINE/CUSTOM
  apiUrl: string
  apiKey: string
  modelName: string
  priority: number
  maxTokens?: number
  temperature?: number
  supportJsonMode: number   // 0/1
  supportStream: number     // 0/1
  usageType: string         // INTERPRET/ANALYSIS/EXPANSION/ALL
  timeoutMs?: number
  retryCount?: number
  isActive: number          // 0/1
}
