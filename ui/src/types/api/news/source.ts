import type { PageDomain, BaseEntity } from '../common'

/** 新闻来源查询参数 */
export interface NewsSourceQueryParams extends PageDomain {
  name?: string
  type?: string
  status?: string
}

/** 新闻来源 */
export interface NewsSource extends BaseEntity {
  id?: number
  name?: string
  type?: string
  url?: string
  fetchType?: string
  fetchInterval?: number
  fetchConfig?: string
  status?: string
}
