/** 新闻解读记录 */
export interface NewsInterpretation {
  id?: number
  articleId?: number
  promptConfigId?: number
  promptSnapshot?: string
  content?: string
  /** 状态：0=进行中 1=完成 2=失败 */
  status?: string
  errorMsg?: string
  tokensUsed?: number
  modelName?: string
  /** 第几次解读（从1开始） */
  interpretCount?: number
  createBy?: string
  createTime?: string
  updateTime?: string
}

/** SSE start 事件数据 */
export interface InterpretStartData {
  recordId: number
  interpretCount: number
}

/** SSE done 事件数据 */
export interface InterpretDoneData {
  modelName?: string
}
