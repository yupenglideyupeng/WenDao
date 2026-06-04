import type { PageDomain, BaseEntity } from '../common'

export interface NewsPromptConfigQueryParams extends PageDomain {
  promptType?: string
  isActive?: number
}

export interface NewsPromptConfig extends BaseEntity {
  id?: number
  typeNames?: string
  typeConfigIds?: number[]
  promptType?: string
  systemPrompt?: string
  /** 温度：控制输出随机性，0=稳定 1=较随机 2=非常随机，分析类建议0.1-0.5 */
  temperature?: number
  /** 最大长度：限制AI单次输出最大token数，500≈300字，2000≈1200字 */
  maxTokens?: number
  isActive?: number
}
