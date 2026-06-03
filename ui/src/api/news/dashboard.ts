import request from '@/utils/request'
import type { DashboardStats, NewsArticle, AjaxResult } from '@/types'

/** 获取大屏统计数据 */
export function getDashboardStats(): Promise<AjaxResult<DashboardStats>> {
  return request({ url: '/news/dashboard/stats', method: 'get' })
}

/** 获取最新文章列表 */
export function getLatestArticles(limit?: number): Promise<AjaxResult<NewsArticle[]>> {
  return request({ url: '/news/dashboard/latest', method: 'get', params: { limit } })
}

/** 获取在线客户端数 */
export function getOnlineCount(): Promise<AjaxResult<{ onlineCount: number }>> {
  return request({ url: '/news/dashboard/onlineCount', method: 'get' })
}
