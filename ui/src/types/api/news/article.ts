import type { PageDomain, BaseEntity } from '../common'

/** 新闻文章查询参数 */
export interface NewsArticleQueryParams extends PageDomain {
  title?: string
  sourceId?: number
  language?: string
  sentiment?: string
  isPushed?: string
  typeConfigId?: number
  status?: string
  fetchOrigin?: string
  beginTime?: string
  endTime?: string
}

/** 新闻文章 */
export interface NewsArticle extends BaseEntity {
  id?: number
  sourceId?: number
  sourceName?: string
  title?: string
  summary?: string
  content?: string
  originalUrl?: string
  language?: string
  tags?: string
  sentiment?: string
  keywords?: string
  publishTime?: string
  fetchTime?: string
  isPushed?: string
  pushTime?: string
  readCount?: number
  status?: string
  typeConfigId?: number
  typeName?: string
  fetchOrigin?: string
  relevance?: number
  isReal?: number
  importance?: string
  relevanceReason?: string
  keywordId?: number
}
