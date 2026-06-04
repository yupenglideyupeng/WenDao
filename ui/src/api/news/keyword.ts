import request from '@/utils/request'
import type { NewsKeywordQueryParams, NewsKeyword, AjaxResult, TableDataInfo } from '@/types'

/** 查询关键词列表 */
export function listKeyword(query: NewsKeywordQueryParams): Promise<TableDataInfo<NewsKeyword[]>> {
  return request({ url: '/news/keyword/list', method: 'get', params: query })
}

/** 查询关键词详情 */
export function getKeyword(id: number): Promise<AjaxResult<NewsKeyword>> {
  return request({ url: '/news/keyword/' + id, method: 'get' })
}

/** 新增关键词 */
export function addKeyword(data: NewsKeyword): Promise<AjaxResult> {
  return request({ url: '/news/keyword', method: 'post', data })
}

/** 修改关键词 */
export function updateKeyword(data: NewsKeyword): Promise<AjaxResult> {
  return request({ url: '/news/keyword', method: 'put', data })
}

/** 删除关键词 */
export function delKeyword(ids: number | number[]): Promise<AjaxResult> {
  return request({ url: '/news/keyword/' + ids, method: 'delete' })
}
