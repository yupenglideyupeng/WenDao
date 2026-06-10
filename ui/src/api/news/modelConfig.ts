import request from '@/utils/request'
import type { NewsModelConfig, NewsModelConfigQueryParams } from '@/types/api/news/modelConfig'
import type { AjaxResult, TableDataInfo } from '@/types/api/common'

/** 分页列表 */
export function listModelConfig(query: NewsModelConfigQueryParams): Promise<TableDataInfo<NewsModelConfig[]>> {
  return request({ url: '/news/model/list', method: 'get', params: query })
}

/** 详情 */
export function getModelConfig(id: number): Promise<AjaxResult<NewsModelConfig>> {
  return request({ url: '/news/model/' + id, method: 'get' })
}

/** 新增 */
export function addModelConfig(data: NewsModelConfig): Promise<AjaxResult> {
  return request({ url: '/news/model', method: 'post', data })
}

/** 修改 */
export function updateModelConfig(data: NewsModelConfig): Promise<AjaxResult> {
  return request({ url: '/news/model', method: 'put', data })
}

/** 删除 */
export function delModelConfig(ids: number | number[]): Promise<AjaxResult> {
  return request({ url: '/news/model/' + ids, method: 'delete' })
}

/** 测试连接 */
export function testModelConfig(id: number): Promise<AjaxResult<{ success: boolean; message: string; modelName?: string }>> {
  return request({ url: '/news/model/test/' + id, method: 'post' })
}

/** AI模型可用性状态 */
export interface ModelStatus {
  totalModels: number
  activeModels: number
  scenes: Record<string, boolean>
  modelNames: Record<string, string>
}

export function getModelStatus(): Promise<AjaxResult<ModelStatus>> {
  return request({ url: '/news/model/status', method: 'get' })
}
