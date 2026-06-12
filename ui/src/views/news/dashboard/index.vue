<template>
  <div class="news-dashboard-wrapper">
    <div class="news-dashboard">
      <!-- 头部 -->
      <header class="dashboard-header">
      <div class="header-left">
        <h1>🤖 AI 新闻实时大屏</h1>
        <span class="header-time">{{ currentTime }}</span>
      </div>
      <div class="header-right">
        <el-tag :type="wsConnected ? 'success' : 'danger'" effect="dark" size="large">
          {{ wsConnected ? '🟢 实时连接中' : '🔴 连接断开' }}
        </el-tag>
        <span class="online-count" v-if="wsConnected">在线客户端: {{ onlineCount }}</span>
      </div>
    </header>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.totalArticles || 0 }}</div>
          <div class="stat-label">文章总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #67c23a">{{ stats.todayArticles || 0 }}</div>
          <div class="stat-label">今日新增</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #409eff">{{ domesticCount }}</div>
          <div class="stat-label">国内来源</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #e6a23c">{{ foreignCount }}</div>
          <div class="stat-label">国外来源</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主体内容 -->
    <el-row :gutter="20" class="main-row">
      <!-- 左侧：实时推送 -->
      <el-col :span="8">
        <el-card class="feed-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📡 实时推送</span>
              <el-badge :value="feedArticles.length" class="item" />
            </div>
          </template>
          <div class="feed-container" ref="feedContainerRef">
            <TransitionGroup name="feed-item">
              <div v-for="article in feedArticles" :key="article.id" class="feed-item">
                <div class="feed-item-header">
                  <el-tag size="small" :type="article.language === 'zh' ? '' : 'info'">
                    {{ article.language === 'zh' ? '国内' : '国外' }}
                  </el-tag>
                  <el-tag size="small" :type="sentimentType(article.sentiment)" v-if="article.sentiment">
                    {{ sentimentLabel(article.sentiment) }}
                  </el-tag>
                  <span class="feed-source">{{ article.sourceName }}</span>
                  <el-button
                    size="small"
                    type="warning"
                    text
                    class="feed-interpret-btn"
                    @click="handleInterpret(article)"
                  >🤖 解读</el-button>
                </div>
                <div class="feed-title">
                  <a :href="article.originalUrl" target="_blank">{{ article.title }}</a>
                </div>
                <div class="feed-summary" v-if="article.summary">{{ truncateText(article.summary, 80) }}</div>
                <div class="feed-time">{{ article.publishTime }}</div>
              </div>
            </TransitionGroup>
          </div>
        </el-card>
      </el-col>

      <!-- 中间：图表 -->
      <el-col :span="10">
        <el-card class="chart-card" shadow="hover">
          <template #header><span>📊 来源分布</span></template>
          <div ref="sourceChartRef" class="chart-box"></div>
        </el-card>
        <el-card class="chart-card" shadow="hover" style="margin-top: 15px;">
          <template #header><span>📈 24小时趋势</span></template>
          <div ref="timelineChartRef" class="chart-box"></div>
        </el-card>
      </el-col>

      <!-- 右侧 -->
      <el-col :span="6">
        <el-card class="chart-card" shadow="hover">
          <template #header><span>😊 情感分析</span></template>
          <div ref="sentimentChartRef" class="chart-box"></div>
        </el-card>
        <el-card class="chart-card" shadow="hover" style="margin-top: 15px;">
          <template #header><span>🏷️ 热门标签</span></template>
          <div class="tag-cloud">
            <el-tag v-for="tag in hotTags" :key="tag" size="large" effect="plain" style="margin: 4px;">
              {{ tag }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
    </div>

    <!-- 一键解读对话框 -->
    <NewsInterpretDialog
      v-model="interpretOpen"
      :article-id="interpretArticleId"
      :article-title="interpretArticleTitle"
    />
  </div>
</template>

<script setup lang="ts" name="NewsDashboard">
import * as echarts from 'echarts'
import { getToken } from '@/utils/auth'
import { getDashboardStats, getLatestArticles, getOnlineCount } from '@/api/news/dashboard'
import type { DashboardStats } from '@/types/api/news/dashboard'
import type { NewsArticle } from '@/types/api/news/article'
import NewsInterpretDialog from '@/views/news/article/NewsInterpretDialog.vue'

const currentTime = ref('')
let timeTimer: number
const wsConnected = ref(false)
const onlineCount = ref(0)

// 统计
const stats = ref<DashboardStats>({})

// 实时推送列表
const feedArticles = ref<NewsArticle[]>([])
const feedContainerRef = ref<HTMLDivElement>()
const MAX_FEED = 100

// 解读对话框状态
const interpretOpen = ref(false)
const interpretArticleId = ref<number | null>(null)
const interpretArticleTitle = ref('')

function handleInterpret(article: NewsArticle) {
  interpretArticleId.value = article.id!
  interpretArticleTitle.value = article.title || ''
  interpretOpen.value = true
}

// WebSocket
let ws: WebSocket | null = null
let reconnectTimer: number
let wsDestroyed = false    // 组件销毁标记，阻止销毁后重连

// 图表
const sourceChartRef = ref<HTMLDivElement>()
const sentimentChartRef = ref<HTMLDivElement>()
const timelineChartRef = ref<HTMLDivElement>()
let sourceChart: echarts.ECharts | null = null
let sentimentChart: echarts.ECharts | null = null
let timelineChart: echarts.ECharts | null = null

// 国内/国外计数
const domesticCount = ref(0)
const foreignCount = ref(0)
const hotTags = ref<string[]>([])

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', { hour12: false })
}
timeTimer = window.setInterval(updateTime, 1000)
updateTime()

function connectWebSocket() {
  const token = getToken()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/news?token=${token}`
  ws = new WebSocket(wsUrl)
  ws.onopen = () => {
    wsConnected.value = true
    console.log('WebSocket connected')
  }
  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'NEW_ARTICLE' && msg.data) {
        feedArticles.value.unshift(msg.data)
        if (feedArticles.value.length > MAX_FEED) {
          feedArticles.value = feedArticles.value.slice(0, MAX_FEED)
        }
        // 更新统计数据
        loadStats()
      }
    } catch (e) {
      console.error('WebSocket message parse error:', e)
    }
  }
  ws.onclose = () => {
    wsConnected.value = false
    if (wsDestroyed) return
    reconnectTimer = window.setTimeout(connectWebSocket, 5000)
  }
  ws.onerror = (err) => {
    console.error('WebSocket error:', err)
    ws?.close()
  }
}

async function loadStats() {
  try {
    const res = await getDashboardStats()
    if (res.data) {
      stats.value = res.data
      domesticCount.value = res.data.domesticCount || 0
      foreignCount.value = res.data.foreignCount || 0
      hotTags.value = (res.data.hotTags || []).map((t: any) => t.name)
      renderCharts()
    }
  } catch (e) {
    console.error('Load stats error:', e)
  }
}

async function loadOnlineCount() {
  try {
    const res = await getOnlineCount()
    if (res.data) {
      onlineCount.value = res.data.onlineCount
    }
  } catch (e) {
    console.error('Load online count error:', e)
  }
}

async function loadLatest() {
  try {
    const res = await getLatestArticles(20)
    if (res.data) {
      feedArticles.value = res.data
    }
  } catch (e) {
    console.error('Load latest error:', e)
  }
}

function renderCharts() {
  // 来源分布饼图
  if (sourceChartRef.value && stats.value.sourceDistribution) {
    if (!sourceChart) {
      sourceChart = echarts.init(sourceChartRef.value)
    }
    sourceChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: stats.value.sourceDistribution,
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
      }]
    })
  }

  // 情感分布饼图
  if (sentimentChartRef.value && stats.value.sentimentDistribution) {
    if (!sentimentChart) {
      sentimentChart = echarts.init(sentimentChartRef.value)
    }
    const sentimentColors: Record<string, string> = { positive: '#67c23a', neutral: '#909399', negative: '#f56c6c' }
    const sentimentLabels: Record<string, string> = { positive: '积极', neutral: '中性', negative: '消极' }
    sentimentChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: '70%',
        data: stats.value.sentimentDistribution.map((d: any) => ({
          ...d,
          name: sentimentLabels[d.name] || d.name,
          itemStyle: { color: sentimentColors[d.name] || '#909399' }
        })),
        label: { formatter: '{b}: {c}' }
      }]
    })
  }

  // 24小时趋势折线图
  if (timelineChartRef.value && stats.value.timelineData) {
    if (!timelineChart) {
      timelineChart = echarts.init(timelineChartRef.value)
    }
    timelineChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: stats.value.timelineData.map((d: any) => d.hour), axisLabel: { rotate: 45 } },
      yAxis: { type: 'value' },
      series: [{
        type: 'line',
        data: stats.value.timelineData.map((d: any) => d.count),
        smooth: true,
        areaStyle: { opacity: 0.3 }
      }]
    })
  }
}

function sentimentType(val?: string) {
  if (val === 'positive') return 'success'
  if (val === 'negative') return 'danger'
  return 'info'
}

function sentimentLabel(val?: string) {
  if (val === 'positive') return '积极'
  if (val === 'negative') return '消极'
  return '中性'
}

function truncateText(text: string, maxLen: number) {
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}

onMounted(async () => {
  await loadStats()
  await loadLatest()
  await loadOnlineCount()
  await nextTick()
  renderCharts()
  connectWebSocket()
})

onUnmounted(() => {
  wsDestroyed = true
  clearInterval(timeTimer)
  clearTimeout(reconnectTimer)
  ws?.close()
  sourceChart?.dispose()
  sentimentChart?.dispose()
  timelineChart?.dispose()
})
</script>

<style scoped>
.news-dashboard-wrapper {
  width: 100%;
  min-height: 100vh;
}
.news-dashboard {
  padding: 0;
  background: linear-gradient(135deg, #0f1923 0%, #1a2332 100%);
  min-height: 100vh;
  color: #e0e0e0;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 30px;
  background: rgba(255, 255, 255, 0.05);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.dashboard-header h1 {
  margin: 0;
  font-size: 24px;
  color: #fff;
}

.header-time {
  font-size: 16px;
  color: #909399;
  margin-left: 20px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.online-count {
  color: #909399;
  font-size: 14px;
}

.stats-row {
  padding: 20px 30px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.08) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

.stat-value {
  font-size: 36px;
  font-weight: bold;
  text-align: center;
  color: #fff;
}

.stat-label {
  text-align: center;
  color: #909399;
  margin-top: 8px;
}

.main-row {
  padding: 0 30px 30px;
}

.feed-card, .chart-card {
  background: rgba(255, 255, 255, 0.08) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #fff;
}

.feed-container {
  max-height: 600px;
  overflow-y: auto;
}

.feed-item {
  padding: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.3s;
}

.feed-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.feed-item-header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.feed-source {
  color: #909399;
  font-size: 12px;
}

.feed-interpret-btn {
  margin-left: auto;
  font-size: 12px;
  padding: 0 4px;
  height: 20px;
  color: #e6a23c !important;
}
.feed-interpret-btn:hover {
  color: #f39c12 !important;
}

.feed-title a {
  color: #409eff;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
}

.feed-title a:hover {
  text-decoration: underline;
}

.feed-summary {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.feed-time {
  color: #606266;
  font-size: 11px;
  margin-top: 4px;
}

.chart-box {
  width: 100%;
  height: 250px;
}

.tag-cloud {
  min-height: 80px;
  padding: 10px;
}

/* 过渡动画 */
.feed-item-enter-active {
  transition: all 0.5s ease;
}
.feed-item-leave-active {
  transition: all 0.3s ease;
}
.feed-item-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}
.feed-item-leave-to {
  opacity: 0;
}

/* 滚动条样式 */
.feed-container::-webkit-scrollbar {
  width: 4px;
}
.feed-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}
</style>
