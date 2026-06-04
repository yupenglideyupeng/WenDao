import request from '@/utils/request'
import type { NewsTypeConfigQueryParams, NewsTypeConfig, AjaxResult, TableDataInfo } from '@/types'

export function listType(query: NewsTypeConfigQueryParams): Promise<TableDataInfo<NewsTypeConfig[]>> {
  return request({ url: '/news/typeConfig/list', method: 'get', params: query })
}
export function getType(id: number): Promise<AjaxResult<NewsTypeConfig>> {
  return request({ url: '/news/typeConfig/' + id, method: 'get' })
}
export function addType(data: NewsTypeConfig): Promise<AjaxResult> {
  return request({ url: '/news/typeConfig', method: 'post', data })
}
export function updateType(data: NewsTypeConfig): Promise<AjaxResult> {
  return request({ url: '/news/typeConfig', method: 'put', data })
}
export function delType(ids: number | number[]): Promise<AjaxResult> {
  return request({ url: '/news/typeConfig/' + ids, method: 'delete' })
}
