import request from '@/utils/request'
import type { NewsPromptConfigQueryParams, NewsPromptConfig, AjaxResult, TableDataInfo } from '@/types'

export function listPrompt(query: NewsPromptConfigQueryParams): Promise<TableDataInfo<NewsPromptConfig[]>> {
  return request({ url: '/news/promptConfig/list', method: 'get', params: query })
}
export function getPrompt(id: number): Promise<AjaxResult<NewsPromptConfig>> {
  return request({ url: '/news/promptConfig/' + id, method: 'get' })
}
export function addPrompt(data: NewsPromptConfig): Promise<AjaxResult> {
  return request({ url: '/news/promptConfig', method: 'post', data })
}
export function updatePrompt(data: NewsPromptConfig): Promise<AjaxResult> {
  return request({ url: '/news/promptConfig', method: 'put', data })
}
export function delPrompt(ids: number | number[]): Promise<AjaxResult> {
  return request({ url: '/news/promptConfig/' + ids, method: 'delete' })
}
