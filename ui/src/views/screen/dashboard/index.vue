<template>
  <div class="screen-dashboard">
    <!-- 顶部导航栏 -->
    <header class="screen-header">
      <div class="header-left">
        <a href="/" class="back-link" title="返回管理端">
          <el-icon><ArrowLeft /></el-icon>
          <span>管理端</span>
        </a>
        <div class="header-divider"></div>
        <h1 class="header-title">闻道 AI 热点监控</h1>
      </div>
      <div class="header-center">
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            v-model="searchText"
            class="search-input"
            placeholder="搜索新闻标题或关键词..."
            @input="onSearchInput"
          />
          <span v-if="searchText" class="search-clear" @click="searchText = ''; onServerFilterChange()">x</span>
        </div>
      </div>
      <div class="header-right">
        <div class="header-stat">
          <span class="hs-value">{{ stats.totalArticles || 0 }}</span>
          <span class="hs-label">总量</span>
        </div>
        <div class="header-stat">
          <span class="hs-value accent-green">{{ stats.todayArticles || 0 }}</span>
          <span class="hs-label">今日</span>
        </div>
        <div class="connection-badge" :class="{ connected: wsConnected }">
          <span class="conn-dot"></span>
          {{ wsConnected ? '实时连接' : '已断开' }}
          <template v-if="wsConnected && onlineCount > 0">
            <span class="conn-sep">|</span>
            {{ onlineCount }} 在线
          </template>
        </div>
        <span class="header-time">{{ currentTime }}</span>
      </div>
    </header>

    <!-- 筛选工具栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <span class="filter-label">来源</span>
        <el-select
          v-model="sourceFilter"
          placeholder="全部"
          size="small"
          clearable
          class="dark-select source-select"
          @change="onServerFilterChange"
        >
          <el-option v-for="src in sourceOptions" :key="src.id" :label="src.name" :value="src.name || ''" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">情感</span>
        <el-select
          v-model="sentimentFilter"
          placeholder="全部"
          size="small"
          clearable
          class="dark-select"
          @change="onServerFilterChange"
        >
          <el-option label="积极" value="positive" />
          <el-option label="中性" value="neutral" />
          <el-option label="消极" value="negative" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">日期</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          size="small"
          range-separator="~"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          class="dark-date-picker"
          @change="onServerFilterChange"
        />
      </div>
      <div class="filter-group">
        <span class="filter-label">类型</span>
        <el-select
          v-model="typeFilter"
          placeholder="全部"
          size="small"
          clearable
          class="dark-select"
          @change="onServerFilterChange"
        >
          <el-option v-for="t in typeOptions" :key="t.id" :label="t.typeName" :value="t.id" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">排序</span>
        <button class="filter-chip" :class="{ active: sortBy === 'time' }" @click="sortBy = 'time'; filterRealtime()">最新</button>
        <button class="filter-chip" :class="{ active: sortBy === 'source' }" @click="sortBy = 'source'; filterRealtime()">来源</button>
      </div>
      <div class="filter-info">
        <span class="feed-count-badge">{{ filteredRealtime.length + historyArticles.length }}</span> 条
        <span v-if="totalHistory > 0" style="margin-left: 4px; color: #484a4e;">/ 共 {{ totalHistory }}</span>
      </div>
    </div>

    <!-- 主体内容 -->
    <div class="main-content">
      <!-- 左侧：新闻 Feed（核心区域） -->
      <div class="panel feed-panel">
        <div class="feed-container" ref="feedContainerRef" @scroll="onFeedScroll">
          <!-- 实时推送区 -->
          <div v-if="filteredRealtime.length > 0" class="realtime-zone">
            <div class="realtime-label">
              <span class="rt-dot"></span>
              实时推送
              <span class="rt-count">{{ filteredRealtime.length }}</span>
            </div>
            <div v-for="article in filteredRealtime" :key="'rt-' + article.id" class="feed-card">
              <div class="card-meta">
                <span class="meta-source" :class="article.language === 'zh' ? 'src-domestic' : 'src-foreign'">
                  {{ article.sourceName }}
                </span>
                <span v-if="article.sentiment" class="meta-sentiment" :class="'s-' + article.sentiment">
                  {{ sentimentMap[article.sentiment] || '中性' }}
                </span>
                <span class="meta-time">{{ formatTime(article.publishTime || article.fetchTime) }}</span>
                <button class="card-interpret-btn" @click="handleInterpret(article)">AI 解读</button>
              </div>
              <div class="card-title">
                <a :href="article.originalUrl" target="_blank" rel="noopener">{{ article.title }}</a>
              </div>
              <div class="card-summary" v-if="article.summary">{{ truncateText(article.summary, 120) }}</div>
              <div class="card-tags" v-if="parseTags(article.tags).length">
                <span v-for="tag in parseTags(article.tags).slice(0, 4)" :key="tag" class="card-tag">{{ tag }}</span>
              </div>
            </div>
            <div class="zone-divider"></div>
          </div>

          <!-- 历史记录区（无限滚动） -->
          <div class="history-zone">
            <div v-for="article in historyArticles" :key="'h-' + article.id" class="feed-card">
              <div class="card-meta">
                <span class="meta-source" :class="article.language === 'zh' ? 'src-domestic' : 'src-foreign'">
                  {{ article.sourceName }}
                </span>
                <span v-if="article.sentiment" class="meta-sentiment" :class="'s-' + article.sentiment">
                  {{ sentimentMap[article.sentiment] || '中性' }}
                </span>
                <span class="meta-time">{{ formatTime(article.publishTime || article.fetchTime) }}</span>
                <button class="card-interpret-btn" @click="handleInterpret(article)">AI 解读</button>
              </div>
              <div class="card-title">
                <a :href="article.originalUrl" target="_blank" rel="noopener">{{ article.title }}</a>
              </div>
              <div class="card-summary" v-if="article.summary">{{ truncateText(article.summary, 120) }}</div>
              <div class="card-tags" v-if="parseTags(article.tags).length">
                <span v-for="tag in parseTags(article.tags).slice(0, 4)" :key="tag" class="card-tag">{{ tag }}</span>
              </div>
            </div>
          </div>

          <!-- 加载状态 -->
          <div class="feed-loading" v-if="isLoadingMore">
            <span class="loading-spinner"></span>
            <span>加载中...</span>
          </div>
          <div class="feed-end" v-else-if="!hasMore && historyArticles.length > 0">
            已加载全部
          </div>
          <div v-if="filteredRealtime.length === 0 && historyArticles.length === 0 && !isLoadingMore" class="feed-empty">
            <span>暂无匹配的新闻</span>
          </div>
        </div>
      </div>

      <!-- 右侧：数据面板 -->
      <div class="side-panel">
        <!-- 概览统计 -->
        <div class="side-section overview-section">
          <div class="overview-grid">
            <div class="ov-item">
              <div class="ov-num">{{ domesticCount }}</div>
              <div class="ov-label">国内</div>
            </div>
            <div class="ov-item">
              <div class="ov-num">{{ foreignCount }}</div>
              <div class="ov-label">国外</div>
            </div>
            <div class="ov-item">
              <div class="ov-num accent-blue">{{ realtimeArticles.length }}</div>
              <div class="ov-label">实时</div>
            </div>
            <div class="ov-item">
              <div class="ov-num accent-orange">{{ sourceOptions.length }}</div>
              <div class="ov-label">来源</div>
            </div>
          </div>
        </div>

        <!-- 24小时趋势 -->
        <div class="side-section">
          <div class="section-title">24H 趋势</div>
          <div ref="timelineChartRef" class="mini-chart"></div>
        </div>

        <!-- 来源分布 -->
        <div class="side-section">
          <div class="section-title">来源分布</div>
          <div ref="sourceChartRef" class="mini-chart chart-md"></div>
        </div>

        <!-- 情感分析 -->
        <div class="side-section">
          <div class="section-title">情感分析</div>
          <div ref="sentimentChartRef" class="mini-chart"></div>
        </div>

        <!-- 热门标签 -->
        <div class="side-section tags-section">
          <div class="section-title">热门标签</div>
          <div class="tag-cloud">
            <span v-for="tag in hotTags" :key="tag" class="hot-tag">{{ tag }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 一键解读对话框 -->
    <NewsInterpretDialog
      v-model="interpretOpen"
      :article-id="interpretArticleId"
      :article-title="interpretArticleTitle"
    />
  </div>
</template>

<script setup lang="ts" name="ScreenDashboard">
import * as echarts from 'echarts'
import { ArrowLeft, Search } from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'
import { getToken } from '@/utils/auth'
import { getDashboardStats, getLatestArticles, getOnlineCount, getDashboardFeed } from '@/api/news/dashboard'
import { listType } from '@/api/news/typeConfig'
import { listSource } from '@/api/news/source'
import type { DashboardStats } from '@/types/api/news/dashboard'
import type { NewsArticle, NewsArticleQueryParams } from '@/types/api/news/article'
import type { NewsTypeConfig } from '@/types/api/news/typeConfig'
import type { NewsSource } from '@/types/api/news/source'
import NewsInterpretDialog from '@/views/news/article/NewsInterpretDialog.vue'

// 情感标签中文映射
const sentimentMap: Record<string, string> = {
  positive: '积极',
  negative: '消极',
  neutral: '中性'
}

const currentTime = ref('')
let timeTimer: number
const wsConnected = ref(false)
const onlineCount = ref(0)

// 统计
const stats = ref<DashboardStats>({})

// === 双区域数据 ===
const realtimeArticles = ref<NewsArticle[]>([])   // WebSocket 实时推送
const historyArticles = ref<NewsArticle[]>([])     // 服务端分页历史
const loadedIds = new Set<number>()                // 去重集合
const MAX_REALTIME = 50

// 分页
const pageNum = ref(1)
const pageSize = 20
const totalHistory = ref(0)
const isLoadingMore = ref(false)
const hasMore = computed(() => historyArticles.value.length < totalHistory.value)

// 筛选
const searchText = ref('')
const sourceFilter = ref('')
const sentimentFilter = ref('')
const sortBy = ref<'time' | 'source'>('time')
const dateRange = ref<string[]>([])
const typeFilter = ref<number | ''>('')
const typeOptions = ref<NewsTypeConfig[]>([])
const sourceOptions = ref<NewsSource[]>([])


// 实时区客户端过滤结果
const filteredRealtime = ref<NewsArticle[]>([])

const feedContainerRef = ref<HTMLDivElement>()

// 解读对话框
const interpretOpen = ref(false)
const interpretArticleId = ref<number | null>(null)
const interpretArticleTitle = ref('')

// WebSocket
let ws: WebSocket | null = null
let reconnectTimer: number
let searchDebounceTimer: number | null = null

// 图表
const sourceChartRef = ref<HTMLDivElement>()
const sentimentChartRef = ref<HTMLDivElement>()
const timelineChartRef = ref<HTMLDivElement>()
let sourceChart: echarts.ECharts | null = null
let sentimentChart: echarts.ECharts | null = null
let timelineChart: echarts.ECharts | null = null

const domesticCount = ref(0)
const foreignCount = ref(0)
const hotTags = ref<string[]>([])

// ========== 基础工具 ==========

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
}

function truncateText(text: string, maxLen: number) {
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}

function parseTags(tags?: string | any): string[] {
  if (!tags) return []
  if (Array.isArray(tags)) return tags
  try {
    const parsed = JSON.parse(tags)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// ========== 数据加载 ==========

async function loadStats() {
  try {
    const res = await getDashboardStats()
    if (res.data) {
      stats.value = res.data
      domesticCount.value = res.data.domesticCount || 0
      foreignCount.value = res.data.foreignCount || 0
      hotTags.value = (res.data.hotTags || []).map((t: any) => t.name)
      await nextTick()
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

async function loadTypeOptions() {
  try {
    const res = await listType({ pageNum: 1, pageSize: 100, isActive: 1 })
    if (res.rows) {
      typeOptions.value = res.rows
    }
  } catch (e) {
    console.error('Load type options error:', e)
  }
}

async function loadSourceOptions() {
  try {
    const res = await listSource({ pageNum: 1, pageSize: 200, status: '0' })
    if (res.rows) {
      sourceOptions.value = res.rows
    }
  } catch (e) {
    console.error('Load source options error:', e)
  }
}

async function loadLatest() {
  try {
    const res = await getLatestArticles(50)
    if (res.data) {
      realtimeArticles.value = res.data
      loadedIds.clear()
      for (const a of res.data) {
        if (a.id) loadedIds.add(a.id)
      }
      filterRealtime()
      // 加载历史第一页
      await loadHistoryPage(false)
    }
  } catch (e) {
    console.error('Load latest error:', e)
  }
}

/**
 * 加载历史分页数据
 * @param append true=追加到现有列表，false=替换
 */
async function loadHistoryPage(append: boolean = false) {
  try {
    const params: NewsArticleQueryParams = {
      pageNum: pageNum.value,
      pageSize,
      status: '0'
    }
    // 搜索
    if (searchText.value.trim()) {
      params.title = searchText.value.trim()
    }
    // 来源（通过名称查找ID）
    if (sourceFilter.value) {
      const src = sourceOptions.value.find((s: NewsSource) => s.name === sourceFilter.value)
      if (src && src.id) params.sourceId = src.id
    }
    // 情感
    if (sentimentFilter.value) {
      params.sentiment = sentimentFilter.value
    }
    // 新闻类型
    if (typeFilter.value) {
      params.typeConfigId = typeFilter.value
    }
    // 日期范围
    if (dateRange.value && dateRange.value.length === 2) {
      params.beginTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }

    const res = await getDashboardFeed(params)
    if (res.rows) {
      // 去重：过滤掉已在实时区显示的
      const newItems = res.rows.filter(a => !loadedIds.has(a.id!))
      totalHistory.value = res.total || 0

      if (append) {
        historyArticles.value = [...historyArticles.value, ...newItems]
      } else {
        historyArticles.value = newItems
      }
      // 将新加载的加入去重集合
      for (const a of newItems) {
        if (a.id) loadedIds.add(a.id)
      }
    }
  } catch (e) {
    console.error('Load history page error:', e)
  }
}

// ========== 筛选 ==========

function filterRealtime() {
  let list = [...realtimeArticles.value]
  if (searchText.value.trim()) {
    const kw = searchText.value.trim().toLowerCase()
    list = list.filter(
      a =>
        (a.title && a.title.toLowerCase().includes(kw)) ||
        (a.summary && a.summary.toLowerCase().includes(kw)) ||
        (a.keywords && a.keywords.toLowerCase().includes(kw))
    )
  }
  if (sourceFilter.value) {
    list = list.filter(a => a.sourceName === sourceFilter.value)
  }
  if (sentimentFilter.value) {
    list = list.filter(a => a.sentiment === sentimentFilter.value)
  }
  if (typeFilter.value) {
    list = list.filter(a => a.typeConfigId === typeFilter.value)
  }
  if (dateRange.value && dateRange.value.length === 2) {
    const begin = new Date(dateRange.value[0]).getTime()
    const end = new Date(dateRange.value[1] + ' 23:59:59').getTime()
    list = list.filter(a => {
      const t = new Date(a.publishTime || a.fetchTime || 0).getTime()
      return t >= begin && t <= end
    })
  }
  if (sortBy.value === 'time') {
    list.sort((a, b) => new Date(b.publishTime || b.fetchTime || 0).getTime() - new Date(a.publishTime || a.fetchTime || 0).getTime())
  } else if (sortBy.value === 'source') {
    list.sort((a, b) => (a.sourceName || '').localeCompare(b.sourceName || ''))
  }
  filteredRealtime.value = list
}

/**
 * 服务端筛选条件变更时触发
 */
function onServerFilterChange() {
  pageNum.value = 1
  historyArticles.value = []
  filterRealtime()
  loadHistoryPage(false)
}

/**
 * 搜索输入防抖
 */
function onSearchInput() {
  filterRealtime()
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = window.setTimeout(() => {
    onServerFilterChange()
  }, 400)
}

// ========== 无限滚动 ==========

function onFeedScroll(event: Event) {
  const el = event.target as HTMLElement
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 100 && hasMore.value && !isLoadingMore.value) {
    loadMore()
  }
}

async function loadMore() {
  if (isLoadingMore.value || !hasMore.value) return
  isLoadingMore.value = true
  pageNum.value++
  await loadHistoryPage(true)
  isLoadingMore.value = false
}

// ========== WebSocket ==========

function connectWebSocket() {
  const token = getToken()
  if (!token) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/news?token=${token}`
  ws = new WebSocket(wsUrl)
  ws.onopen = () => {
    wsConnected.value = true
  }
  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'NEW_ARTICLE' && msg.data) {
        const article = msg.data as NewsArticle
        // 去重
        if (article.id && loadedIds.has(article.id)) return
        if (article.id) loadedIds.add(article.id)
        // 加入实时区
        realtimeArticles.value.unshift(article)
        if (realtimeArticles.value.length > MAX_REALTIME) {
          const evicted = realtimeArticles.value.splice(MAX_REALTIME)
          for (const a of evicted) {
            if (a.id) loadedIds.delete(a.id)
          }
        }
        filterRealtime()
        // 通知卡片
        showArticleNotification(article)
        // 刷新统计（延迟，避免频繁请求）
        loadStats()
      }
    } catch (e) {
      console.error('WebSocket message error:', e)
    }
  }
  ws.onclose = () => {
    wsConnected.value = false
    reconnectTimer = window.setTimeout(() => {
      connectWebSocket()
      // 重连后刷新实时区
      loadLatest()
    }, 5000)
  }
  ws.onerror = () => {
    ws?.close()
  }
}

// ========== 通知卡片 ==========

function showArticleNotification(article: NewsArticle) {
  const title = truncateText(article.title || '新新闻', 40)
  const summary = truncateText(article.summary || '', 80)
  ElNotification({
    title,
    message: h('div', { class: 'screen-notif-body' }, [
      h('span', { class: 'notif-source' }, article.sourceName || ''),
      summary ? h('p', { class: 'notif-summary' }, summary) : null
    ]),
    position: 'top-right',
    duration: 4000,
    customClass: 'screen-notification',
    onClick: () => {
      // 点击通知滚动到顶部
      if (feedContainerRef.value) {
        feedContainerRef.value.scrollTop = 0
      }
    }
  })
}

// ========== 解读 ==========

function handleInterpret(article: NewsArticle) {
  interpretArticleId.value = article.id!
  interpretArticleTitle.value = article.title || ''
  interpretOpen.value = true
}

// ========== 图表 ==========

function renderCharts() {
  const darkTooltip = {
    backgroundColor: 'rgba(15,20,35,0.95)',
    borderColor: 'rgba(255,255,255,0.1)',
    textStyle: { color: '#c0c4cc', fontSize: 11 }
  }

  if (timelineChartRef.value && stats.value.timelineData) {
    if (!timelineChart) timelineChart = echarts.init(timelineChartRef.value)
    timelineChart.setOption({
      tooltip: { trigger: 'axis', ...darkTooltip },
      grid: { top: 5, right: 5, bottom: 18, left: 28 },
      xAxis: {
        type: 'category',
        data: stats.value.timelineData.map((d: any) => d.hour),
        axisLabel: { color: '#555', fontSize: 9, interval: 3 },
        axisLine: { show: false },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#555', fontSize: 9 },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)' } },
        axisLine: { show: false }
      },
      series: [{
        type: 'line',
        data: stats.value.timelineData.map((d: any) => d.count),
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#409eff', width: 1.5 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.35)' },
            { offset: 1, color: 'rgba(64,158,255,0.02)' }
          ])
        }
      }]
    })
  }

  if (sourceChartRef.value && stats.value.sourceDistribution) {
    if (!sourceChart) sourceChart = echarts.init(sourceChartRef.value)
    const raw = stats.value.sourceDistribution || []
    const MAX_SOURCES = 8
    let chartData: { name: string; value: number }[]
    if (raw.length > MAX_SOURCES) {
      chartData = raw.slice(0, MAX_SOURCES)
      const otherValue = raw.slice(MAX_SOURCES).reduce((sum: number, d: any) => sum + d.value, 0)
      chartData.push({ name: '其他', value: otherValue })
    } else {
      chartData = raw
    }
    sourceChart.setOption({
      tooltip: { trigger: 'item', ...darkTooltip },
      series: [{
        type: 'pie',
        radius: ['35%', '68%'],
        center: ['50%', '50%'],
        data: chartData,
        label: { color: '#8890a0', fontSize: 10, formatter: '{b}' },
        itemStyle: { borderColor: '#0f1923', borderWidth: 2 },
        emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(64,158,255,0.3)' } }
      }]
    })
  }

  if (sentimentChartRef.value && stats.value.sentimentDistribution) {
    if (!sentimentChart) sentimentChart = echarts.init(sentimentChartRef.value)
    const colorMap: Record<string, string> = { positive: '#67c23a', neutral: '#909399', negative: '#f56c6c' }
    const chartData = stats.value.sentimentDistribution.map((d: any) => ({
      name: sentimentMap[d.name] || d.name,
      value: d.value,
      itemStyle: { color: colorMap[d.name] || '#909399' }
    }))
    sentimentChart.setOption({
      tooltip: { trigger: 'item', ...darkTooltip },
      series: [{
        type: 'pie',
        radius: '65%',
        center: ['50%', '50%'],
        data: chartData,
        label: { color: '#8890a0', fontSize: 11, formatter: '{b} {c}' },
        itemStyle: { borderColor: '#0f1923', borderWidth: 2 }
      }]
    })
  }
}

function handleResize() {
  sourceChart?.resize()
  sentimentChart?.resize()
  timelineChart?.resize()
}

// ========== 生命周期 ==========

onMounted(async () => {
  timeTimer = window.setInterval(updateTime, 1000)
  updateTime()
  await loadStats()
  await loadTypeOptions()
  await loadSourceOptions()
  await loadLatest()
  await loadOnlineCount()
  connectWebSocket()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  clearInterval(timeTimer)
  clearTimeout(reconnectTimer)
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  ws?.close()
  sourceChart?.dispose()
  sentimentChart?.dispose()
  timelineChart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.screen-dashboard {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0a0e1a;
  color: #c8cdd5;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ===== 头部 ===== */
.screen-header {
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 48px;
  background: rgba(255,255,255,0.02);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
  gap: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.back-link {
  display: flex;
  align-items: center;
  gap: 3px;
  color: #606266;
  font-size: 12px;
  text-decoration: none;
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.2s;
}
.back-link:hover { color: #409eff; border-color: #409eff; background: rgba(64,158,255,0.08); }
.header-divider { width: 1px; height: 16px; background: rgba(255,255,255,0.1); }
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #e8ecf0;
  margin: 0;
  white-space: nowrap;
}

.header-center { flex: 1; display: flex; justify-content: center; }
.search-box {
  position: relative;
  width: 320px;
  max-width: 100%;
}
.search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #555;
  font-size: 14px;
}
.search-input {
  width: 100%;
  height: 30px;
  padding: 0 28px 0 32px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 15px;
  color: #c0c4cc;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}
.search-input::placeholder { color: #484a4e; }
.search-input:focus { border-color: rgba(64,158,255,0.5); background: rgba(255,255,255,0.07); }
.search-clear {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #555;
  cursor: pointer;
  font-size: 12px;
}
.search-clear:hover { color: #909399; }

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.header-stat {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.hs-value {
  font-size: 16px;
  font-weight: 700;
  color: #e0e0e0;
  font-family: 'Courier New', monospace;
}
.hs-value.accent-green { color: #67c23a; }
.hs-label { font-size: 11px; color: #555; }
.connection-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #f56c6c;
  padding: 3px 10px;
  border-radius: 12px;
  background: rgba(245,108,108,0.08);
  border: 1px solid rgba(245,108,108,0.15);
}
.connection-badge.connected {
  color: #67c23a;
  background: rgba(103,194,58,0.08);
  border-color: rgba(103,194,58,0.15);
}
.conn-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.connected .conn-dot { animation: pulse 2s infinite; }
.conn-sep { color: rgba(255,255,255,0.15); }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }
.header-time {
  font-size: 12px;
  color: #484a4e;
  font-family: 'Courier New', monospace;
  white-space: nowrap;
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  flex-shrink: 0;
  overflow-x: auto;
}
.filter-group {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.filter-label {
  font-size: 11px;
  color: #484a4e;
  margin-right: 2px;
  white-space: nowrap;
}
.filter-chip {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  color: #606266;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.08);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.filter-chip:hover { color: #909399; border-color: rgba(255,255,255,0.15); }
.filter-chip.active {
  color: #409eff;
  background: rgba(64,158,255,0.12);
  border-color: rgba(64,158,255,0.3);
}
.filter-info {
  margin-left: auto;
  font-size: 11px;
  color: #484a4e;
  white-space: nowrap;
  flex-shrink: 0;
}
.feed-count-badge {
  color: #409eff;
  font-weight: 600;
}

/* 暗色日期选择器 */
.filter-bar :deep(.el-date-editor) {
  --el-fill-color-blank: rgba(255,255,255,0.05);
  --el-border-color: rgba(255,255,255,0.08);
  --el-text-color-regular: #8890a0;
  --el-text-color-placeholder: #484a4e;
  --el-color-primary: #409eff;
  width: 200px !important;
}
.filter-bar :deep(.el-date-editor .el-range-input) {
  background: transparent;
  color: #8890a0;
  font-size: 11px;
}
.filter-bar :deep(.el-date-editor .el-range-separator) {
  color: #484a4e;
  font-size: 11px;
}
/* 暗色下拉框 */
.filter-bar :deep(.el-select) {
  --el-fill-color-blank: rgba(255,255,255,0.05);
  --el-border-color: rgba(255,255,255,0.08);
  --el-text-color-regular: #8890a0;
  --el-text-color-placeholder: #484a4e;
  --el-color-primary: #409eff;
  width: 110px;
}
.filter-bar :deep(.el-select.source-select) {
  width: 150px;
}
.filter-bar :deep(.el-select .el-input__wrapper) {
  background: rgba(255,255,255,0.05);
  border-radius: 10px;
}
.filter-bar :deep(.el-input__inner) {
  font-size: 11px;
}

/* ===== 主体 ===== */
.main-content {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px 20px 16px;
  min-height: 0;
}

/* 左侧 Feed */
.feed-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  background: rgba(255,255,255,0.025);
  border: 1px solid rgba(255,255,255,0.06);
  overflow: hidden;
}
.feed-container {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

/* 实时区 */
.realtime-zone {
  background: rgba(64,158,255,0.02);
}
.realtime-label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px 4px;
  font-size: 11px;
  color: #409eff;
  font-weight: 500;
}
.rt-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #409eff;
  animation: pulse 2s infinite;
}
.rt-count {
  font-size: 10px;
  color: #555;
  font-weight: 400;
}
.zone-divider {
  height: 1px;
  margin: 0 16px;
  background: linear-gradient(90deg, transparent, rgba(64,158,255,0.2), transparent);
}

/* 卡片通用 */
.feed-card {
  padding: 10px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  transition: background 0.2s;
}
.feed-card:hover { background: rgba(255,255,255,0.03); }
.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.meta-source {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
}
.src-domestic { background: rgba(64,158,255,0.15); color: #79bbff; }
.src-foreign { background: rgba(144,147,153,0.15); color: #909399; }
.meta-sentiment {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
}
.s-positive { background: rgba(103,194,58,0.15); color: #95d475; }
.s-negative { background: rgba(245,108,108,0.15); color: #fab6b6; }
.s-neutral { background: rgba(144,147,153,0.12); color: #8890a0; }
.meta-time {
  font-size: 10px;
  color: #404245;
  margin-left: auto;
}
.card-interpret-btn {
  background: none;
  border: 1px solid rgba(230,162,60,0.25);
  color: #e6a23c;
  font-size: 10px;
  padding: 1px 8px;
  border-radius: 3px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
  white-space: nowrap;
}
.feed-card:hover .card-interpret-btn { opacity: 1; }
.card-interpret-btn:hover { background: rgba(230,162,60,0.1); border-color: #e6a23c; }

.card-title a {
  color: #d0d5dc;
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-title a:hover { color: #409eff; }
.card-summary {
  color: #555a62;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-tags {
  display: flex;
  gap: 4px;
  margin-top: 5px;
}
.card-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 2px;
  color: #606266;
  background: rgba(255,255,255,0.04);
}

/* 加载状态 */
.feed-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  color: #484a4e;
  font-size: 12px;
}
.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(64,158,255,0.2);
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.feed-end {
  text-align: center;
  padding: 16px;
  color: #404245;
  font-size: 11px;
}
.feed-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  color: #484a4e;
  font-size: 14px;
}

/* 右侧面板 */
.side-panel {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}
.side-section {
  border-radius: 8px;
  background: rgba(255,255,255,0.025);
  border: 1px solid rgba(255,255,255,0.06);
  padding: 10px 12px;
}
.section-title {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
  font-weight: 500;
}

/* 概览网格 */
.overview-section { padding: 8px 12px; }
.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 4px;
  text-align: center;
}
.ov-num {
  font-size: 18px;
  font-weight: 700;
  color: #e0e0e0;
  font-family: 'Courier New', monospace;
}
.ov-num.accent-blue { color: #409eff; }
.ov-num.accent-orange { color: #e6a23c; }
.ov-label { font-size: 10px; color: #484a4e; margin-top: 1px; }

/* 迷你图表 */
.mini-chart { height: 100px; }
.mini-chart.chart-md { height: 130px; }

/* 标签云 */
.tags-section { flex-shrink: 0; }
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 100px;
  overflow-y: auto;
}
.hot-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(64,158,255,0.1);
  color: #79bbff;
  border: 1px solid rgba(64,158,255,0.15);
}

/* 动画 */
.feed-item-enter-active { transition: all 0.4s ease; }
.feed-item-leave-active { transition: all 0.2s ease; }
.feed-item-enter-from { opacity: 0; transform: translateY(-10px); }
.feed-item-leave-to { opacity: 0; }

/* 滚动条 */
.feed-container::-webkit-scrollbar,
.side-panel::-webkit-scrollbar { width: 3px; }
.feed-container::-webkit-scrollbar-thumb,
.side-panel::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 2px; }
.feed-container::-webkit-scrollbar-track,
.side-panel::-webkit-scrollbar-track { background: transparent; }
</style>

<!-- 通知卡片暗色主题（全局，不能 scoped） -->
<style>
.screen-notification {
  background: #1a1f2e !important;
  border: 1px solid rgba(64,158,255,0.2) !important;
  border-left: 3px solid #409eff !important;
  box-shadow: 0 4px 16px rgba(0,0,0,0.4) !important;
}
.screen-notification .el-notification__title {
  color: #d0d5dc !important;
  font-size: 13px !important;
  font-weight: 500 !important;
}
.screen-notification .el-notification__content {
  color: #8890a0 !important;
}
.screen-notif-body .notif-source {
  font-size: 11px;
  color: #79bbff;
  background: rgba(64,158,255,0.12);
  padding: 1px 6px;
  border-radius: 3px;
}
.screen-notif-body .notif-summary {
  margin: 6px 0 0;
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
}
.screen-notification .el-notification__closeBtn {
  color: #555 !important;
}
.screen-notification .el-notification__closeBtn:hover {
  color: #909399 !important;
}
/* 日期选择器弹出面板暗色 */
.el-popper.is-light .el-date-range-picker {
  --el-bg-color: #1a1f2e;
  --el-bg-color-overlay: #1e2433;
  --el-text-color-regular: #c0c4cc;
  --el-text-color-secondary: #8890a0;
  --el-border-color: rgba(255,255,255,0.1);
  --el-border-color-lighter: rgba(255,255,255,0.06);
  --el-fill-color-light: rgba(64,158,255,0.1);
  --el-color-primary-light-9: rgba(64,158,255,0.15);
}
/* 下拉框弹出面板暗色 */
.el-select-dropdown {
  --el-bg-color: #1a1f2e !important;
  --el-bg-color-overlay: #1e2433 !important;
  --el-text-color-regular: #c0c4cc !important;
  --el-border-color: rgba(255,255,255,0.1) !important;
  --el-fill-color-light: rgba(64,158,255,0.1) !important;
  background: #1a1f2e !important;
  border-color: rgba(255,255,255,0.1) !important;
}
.el-select-dropdown .el-select-dropdown__item {
  color: #c0c4cc;
}
.el-select-dropdown .el-select-dropdown__item.is-hovering {
  background: rgba(64,158,255,0.1);
}
.el-select-dropdown .el-select-dropdown__item.is-selected {
  color: #409eff;
}
</style>
