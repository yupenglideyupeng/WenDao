import request from '@/utils/request'
import type { NewsArticleQueryParams, NewsArticle, AjaxResult, TableDataInfo } from '@/types'

/** 查询新闻文章列表 */
export function listArticle(query: NewsArticleQueryParams): Promise<TableDataInfo<NewsArticle[]>> {
  return request({ url: '/news/article/list', method: 'get', params: query })
}

/** 查询新闻文章详情 */
export function getArticle(id: number): Promise<AjaxResult<NewsArticle>> {
  return request({ url: '/news/article/' + id, method: 'get' })
}

/** 修改新闻文章 */
export function updateArticle(data: NewsArticle): Promise<AjaxResult> {
  return request({ url: '/news/article', method: 'put', data })
}

/** 删除新闻文章 */
export function delArticle(ids: number | number[]): Promise<AjaxResult> {
  return request({ url: '/news/article/' + ids, method: 'delete' })
}

/** 手动推送文章 */
export function pushArticle(id: number): Promise<AjaxResult> {
  return request({ url: '/news/article/push/' + id, method: 'post' })
}
