import type { PageDomain, BaseEntity } from '../common'

/** 新闻关键词查询参数 */
export interface NewsKeywordQueryParams extends PageDomain {
  text?: string
  category?: string
  isActive?: number
}

/** 新闻关键词 */
export interface NewsKeyword extends BaseEntity {
  id?: number
  text?: string
  category?: string
  isActive?: number
  fetchInterval?: number
  lastFetchTime?: string
  relevanceThreshold?: number
  expandQueries?: string
}
