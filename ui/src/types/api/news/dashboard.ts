/** 大屏统计数据 */
export interface DashboardStats {
  totalArticles?: number
  todayArticles?: number
  sourceDistribution?: { name: string; value: number }[]
  sentimentDistribution?: { name: string; value: number }[]
  timelineData?: { hour: string; count: number }[]
}

/** WebSocket消息 */
export interface WebSocketMessage {
  type: 'NEW_ARTICLE' | 'HEARTBEAT'
  data: any
  timestamp: number
}
