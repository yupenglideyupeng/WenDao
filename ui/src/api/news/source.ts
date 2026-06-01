import request from '@/utils/request'
import type { NewsSourceQueryParams, NewsSource, AjaxResult, TableDataInfo } from '@/types'

/** 查询新闻来源列表 */
export function listSource(query: NewsSourceQueryParams): Promise<TableDataInfo<NewsSource[]>> {
  return request({ url: '/news/source/list', method: 'get', params: query })
}

/** 查询新闻来源详情 */
export function getSource(id: number): Promise<AjaxResult<NewsSource>> {
  return request({ url: '/news/source/' + id, method: 'get' })
}

/** 新增新闻来源 */
export function addSource(data: NewsSource): Promise<AjaxResult> {
  return request({ url: '/news/source', method: 'post', data })
}

/** 修改新闻来源 */
export function updateSource(data: NewsSource): Promise<AjaxResult> {
  return request({ url: '/news/source', method: 'put', data })
}

/** 删除新闻来源 */
export function delSource(ids: number | number[]): Promise<AjaxResult> {
  return request({ url: '/news/source/' + ids, method: 'delete' })
}
