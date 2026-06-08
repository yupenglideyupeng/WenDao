<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="1060px"
    top="5vh"
    append-to-body
    destroy-on-close
    @close="handleClose"
  >
    <div class="interpret-layout">
      <!-- 左侧：解读内容区 -->
      <div class="interpret-main">
        <!-- 状态栏 -->
        <div class="interpret-status-bar">
          <div class="status-left">
            <el-tag v-if="status === 'running'" type="warning" effect="dark" class="interpret-status-tag">
              解读中
            </el-tag>
            <el-tag v-else-if="status === 'done'" type="success" effect="dark">解读完成</el-tag>
            <el-tag v-else-if="status === 'error'" type="danger" effect="dark">解读失败</el-tag>
            <el-tag v-else-if="status === 'idle' && !compact" type="info" effect="plain">历史解读</el-tag>

            <span v-if="interpretCount" class="interpret-count">第 {{ interpretCount }} 次解读</span>
            <span v-if="tokensUsed" class="token-info">消耗 {{ tokensUsed }} tokens · {{ modelName }}</span>
          </div>
          <div class="status-right">
            <!-- 导出按钮组 -->
            <el-dropdown
              v-if="(status === 'done' || status === 'idle') && !compact"
              trigger="click"
              @command="handleExport"
              class="export-dropdown"
            >
              <el-button size="small" type="success" plain>
                <el-icon><Download /></el-icon> 导出
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="pdf">
                    <el-icon><Document /></el-icon> 导出 PDF
                  </el-dropdown-item>
                  <el-dropdown-item command="html">
                    <el-icon><Monitor /></el-icon> 导出 HTML
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <!-- 历史记录按钮（精简模式下隐藏） -->
            <el-button
              v-if="!compact"
              size="small"
              :type="historyOpen ? 'primary' : 'default'"
              plain
              @click="toggleHistory"
            >
              <el-icon><Clock /></el-icon> 历史
            </el-button>

            <!-- 重新解读按钮 -->
            <el-button
              v-if="status === 'done' || status === 'error' || status === 'idle'"
              size="small"
              type="primary"
              plain
              :loading="status === 'running'"
              @click="reInterpret"
            >
              重新解读
            </el-button>
          </div>
        </div>

        <!-- 加载中骨架屏 -->
        <div v-if="status === 'running' && !streamContent" class="interpret-skeleton">
          <el-skeleton :rows="8" animated />
        </div>

        <!-- 错误提示 -->
        <el-alert
          v-if="status === 'error'"
          :title="errorMsg || '解读失败，请重新尝试'"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
        />

        <!-- 统一内容展示区（流式输出 + 渲染后的 Markdown） -->
        <div
          v-if="displayContent"
          :key="'content-' + (currentRecordId || 'stream')"
          class="interpret-content"
          :class="status === 'running' ? 'stream-text' : 'markdown-body'"
          ref="contentRef"
          v-html="displayContent"
        ></div>
      </div>

      <!-- 右侧：历史记录面板（精简模式下隐藏） -->
      <transition name="slide">
        <div v-if="historyOpen && !compact" class="interpret-history">
          <div class="history-header">
            <span class="history-title">历史记录</span>
            <el-button link @click="historyOpen = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <div v-if="historyLoading" class="history-loading">
            <el-skeleton :rows="4" animated />
          </div>
          <div v-else-if="historyList.length === 0" class="history-empty">
            <el-empty description="暂无历史记录" :image-size="60" />
          </div>
          <div v-else class="history-list">
            <div
              v-for="item in historyList"
              :key="item.id"
              class="history-item"
              :class="{ active: currentRecordId === item.id }"
              @click="viewHistory(item)"
            >
              <div class="history-item-header">
                <el-tag
                  :type="item.status === '1' ? 'success' : item.status === '0' ? 'warning' : 'danger'"
                  size="small"
                  effect="plain"
                >
                  {{ item.status === '1' ? '完成' : item.status === '0' ? '进行中' : '失败' }}
                </el-tag>
                <span class="history-count">第 {{ item.interpretCount }} 次</span>
              </div>
              <div class="history-item-meta">
                <span>{{ item.createBy }}</span>
                <span>{{ formatTime(item.createTime) }}</span>
              </div>
              <div v-if="item.modelName" class="history-item-model">
                {{ item.modelName }}
                <span v-if="item.tokensUsed"> · {{ item.tokensUsed }} tokens</span>
              </div>
              <div v-if="item.status === '2' && item.errorMsg" class="history-item-error">
                {{ item.errorMsg }}
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="NewsInterpretDialog">
import { Download, Document, Monitor, Clock, Close } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import mermaid from 'mermaid'
import { ElMessage, ElLoading } from 'element-plus'
import { getLatestInterpretation, getInterpretationList, startInterpret } from '@/api/news/interpretation'
import { exportToPdf, exportToHtml } from '@/utils/interpretExport'
import type { NewsInterpretation } from '@/types/api/news/interpretation'

// -----------------------------------------------------------------------
// Props & Emits
// -----------------------------------------------------------------------
const props = defineProps<{
  modelValue: boolean
  articleId: number | null
  articleTitle?: string
  /** 精简模式：隐藏历史记录、导出按钮，打开直接开始解读 */
  compact?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

// -----------------------------------------------------------------------
// Mermaid & Markdown 初始化
// -----------------------------------------------------------------------
mermaid.initialize({
  startOnLoad: false,
  theme: 'default',
  securityLevel: 'loose',
  fontFamily: 'inherit'
})

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

// 自定义 fence 渲染器：将 mermaid 代码块包裹在可识别的 DOM 结构中
const defaultFence = md.renderer.rules.fence!.bind(md.renderer.rules)
md.renderer.rules.fence = (tokens: any[], idx: number, options: any, env: any, self: any) => {
  const token = tokens[idx]
  if (token.info.trim() === 'mermaid') {
    const escaped = md.utils.escapeHtml(token.content)
    return `<div class="mermaid-wrapper"><pre><code class="language-mermaid mermaid">${escaped}</code></pre></div>`
  }
  return defaultFence(tokens, idx, options, env, self)
}

// -----------------------------------------------------------------------
// 状态
// -----------------------------------------------------------------------
type StatusType = '' | 'running' | 'done' | 'error' | 'idle'

const status = ref<StatusType>('')
const streamContent = ref('')
const errorMsg = ref('')
const interpretCount = ref(0)
const tokensUsed = ref<number | undefined>()
const modelName = ref('')
const contentRef = ref<HTMLDivElement>()
const currentRecordId = ref<number | undefined>()

// 历史记录状态
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyList = ref<NewsInterpretation[]>([])

let abortFn: (() => void) | null = null

// -----------------------------------------------------------------------
// 计算属性
// -----------------------------------------------------------------------
const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val)
})

const dialogTitle = computed(() => {
  const title = props.articleTitle ? `「${props.articleTitle}」` : ''
  return `AI 一键解读 ${title}`
})

const renderedHtml = ref('')

/**
 * 统一内容输出：
 * - 流式阶段：转义后的原始文本 + 光标（通过 v-html 安全显示）
 * - 完成/历史阶段：MarkdownIt 渲染后的 HTML
 */
const displayContent = computed(() => {
  if (status.value === 'running' && streamContent.value) {
    // 流式阶段：转义 HTML 特殊字符，追加光标
    return escapeHtml(streamContent.value) + '<span class="typing-cursor">|</span>'
  }
  if ((status.value === 'done' || status.value === 'idle') && renderedHtml.value) {
    return renderedHtml.value
  }
  return ''
})

/**
 * 修复 AI 输出的 Markdown 格式问题
 * - ##Heading → ## Heading（缺少空格的标题标记）
 * - 自动检测未包裹的 mermaid 流程图语法并添加 ```mermaid 代码块
 * - 其他常见格式瑕疵
 */
function normalizeMarkdown(text: string): string {
  // 统一换行符（CRLF / CR → LF），避免后续处理异常
  let result = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  result = result
    // 修复标题标记后缺少空格：##Heading → ## Heading
    .replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
    // 修复列表标记后缺少空格：-Item → - Item
    .replace(/^([*-])([^\s*-])/gm, '$1 $2')
    // 修复有序列表后缺少空格：1.Item → 1. Item
    .replace(/^(\d+\.)([^\s])/gm, '$1 $2')
    // 修复引用标记后缺少空格：>Text → > Text
    .replace(/^(>)([^\s>])/gm, '$1 $2')

  // 自动检测未包裹的 mermaid 流程图语法并包裹为 ```mermaid 代码块
  result = autoWrapMermaid(result)
  return result
}

/**
 * 自动检测并包裹未用 ```mermaid 包裹的 mermaid 流程图语法
 * 特征：连续多行包含 mermaid 连线符号（-->、---、-.->、==>）且不在已有代码块中
 */
function autoWrapMermaid(text: string): string {
  // 如果文本中已包含 ```mermaid，跳过自动包裹
  if (/```mermaid/i.test(text)) return text

  const lines = text.split('\n')
  const mermaidLineRegex = /^[\s]*[A-Za-z0-9_\[\]()"'一-鿿]+\s*(-->|---|==>|-\.->)\s*/
  const graphDeclRegex = /^(graph\s+(TB|TD|BT|RL|LR)|flowchart\s+(TB|TD|BT|RL|LR))/i

  // 扫描连续匹配行，找到 mermaid 代码段
  const segments: Array<{ start: number; end: number }> = []
  let i = 0
  while (i < lines.length) {
    // 跳过已有的代码块（```...```）
    if (/^```/.test(lines[i].trim())) {
      i++
      while (i < lines.length && !/^```/.test(lines[i].trim())) i++
      i++ // 跳过闭合 ```
      continue
    }

    // 检查当前行是否匹配 mermaid 语法
    const isGraphDecl = graphDeclRegex.test(lines[i].trim())
    const isMermaidLine = mermaidLineRegex.test(lines[i])

    if (isGraphDecl || isMermaidLine) {
      const start = i
      i++
      // 继续收集连续匹配的行
      while (i < lines.length) {
        const trimmed = lines[i].trim()
        // 空行或后续 mermaid 连线行继续收集
        if (trimmed === '' || mermaidLineRegex.test(trimmed)) {
          i++
        } else {
          break
        }
      }
      // 只有至少 2 行才视为 mermaid 段（单行可能是误匹配）
      const segmentLineCount = i - start
      const nonEmptyCount = lines.slice(start, i).filter(l => l.trim() !== '').length
      if (nonEmptyCount >= 2) {
        segments.push({ start, end: i })
      }
    } else {
      i++
    }
  }

  if (segments.length === 0) return text

  // 从后往前替换，避免索引偏移
  for (let s = segments.length - 1; s >= 0; s--) {
    const seg = segments[s]
    const segmentLines = lines.slice(seg.start, seg.end)
    // 去除首尾空行
    while (segmentLines.length > 0 && segmentLines[0].trim() === '') segmentLines.shift()
    while (segmentLines.length > 0 && segmentLines[segmentLines.length - 1].trim() === '') segmentLines.pop()

    // 如果段落第一行已是 graph/flowchart 声明，直接包裹
    // 否则默认添加 flowchart TD
    const firstTrimmed = segmentLines[0].trim()
    const hasGraphDecl = graphDeclRegex.test(firstTrimmed)

    const wrapped = [
      '```mermaid',
      hasGraphDecl ? '' : 'flowchart TD',
      ...segmentLines,
      '```'
    ].filter(l => l !== '').join('\n')

    lines.splice(seg.start, seg.end - seg.start, wrapped)
  }

  return lines.join('\n')
}

/** HTML 转义工具 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

// -----------------------------------------------------------------------
// 打开对话框时自动加载
// -----------------------------------------------------------------------
watch(() => props.modelValue, async (val: boolean) => {
  if (val && props.articleId) {
    await loadOrStart()
  }
})

async function loadOrStart() {
  reset()
  // 精简模式：有历史就展示，没有就新解读
  if (props.compact) {
    try {
      const res = await getLatestInterpretation(props.articleId!)
      const record: NewsInterpretation | null = res.data ?? null
      if (record && record.status === '1') {
        showExisting(record)
        return
      }
    } catch (_) { /* 查询失败，直接新解读 */ }
    doStartInterpret()
    return
  }
  try {
    const res = await getLatestInterpretation(props.articleId!)
    const record: NewsInterpretation | null = res.data ?? null

    if (record && record.status === '1') {
      // 已有完成的解读记录，直接展示
      showExisting(record)
    } else if (record && record.status === '0') {
      // 上一条仍在进行中，重新发起解读
      doStartInterpret()
    } else {
      // 无记录，首次解读
      doStartInterpret()
    }
  } catch (e) {
    doStartInterpret()
  }
}

function showExisting(record: NewsInterpretation) {
  const content = record.content || ''
  streamContent.value = content
  renderedHtml.value = md.render(normalizeMarkdown(content))
  status.value = 'idle'
  interpretCount.value = record.interpretCount || 0
  tokensUsed.value = record.tokensUsed
  modelName.value = record.modelName || ''
  currentRecordId.value = record.id
  nextTick(() => processMermaidDiagrams())
}

function doStartInterpret() {
  reset()
  status.value = 'running'

  abortFn = startInterpret(props.articleId!, {
    onStart(data) {
      interpretCount.value = data.interpretCount
      currentRecordId.value = data.recordId
    },
    onChunk(text) {
      streamContent.value += text
      nextTick(() => scrollToBottom())
    },
    onDone(data) {
      // 先设置渲染好的 HTML，再改状态
      renderedHtml.value = md.render(normalizeMarkdown(streamContent.value))
      status.value = 'done'
      modelName.value = data.modelName || ''
      nextTick(() => processMermaidDiagrams())
    },
    onError(msg) {
      status.value = 'error'
      errorMsg.value = msg
    }
  })
}

function reInterpret() {
  abortFn?.()
  abortFn = null
  doStartInterpret()
}

// -----------------------------------------------------------------------
// 历史记录功能
// -----------------------------------------------------------------------
async function toggleHistory() {
  historyOpen.value = !historyOpen.value
  if (historyOpen.value && historyList.value.length === 0) {
    await loadHistory()
  }
}

async function loadHistory() {
  if (!props.articleId) return
  historyLoading.value = true
  try {
    const res = await getInterpretationList(props.articleId)
    historyList.value = res.data ?? []
  } catch (e) {
    console.error('加载历史记录失败:', e)
    ElMessage.error('加载历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

function viewHistory(record: NewsInterpretation) {
  // 中止当前流（如果正在解读）
  abortFn?.()
  abortFn = null

  if (record.status === '1') {
    // 完成的记录：展示内容
    const content = record.content || ''
    streamContent.value = content
    // 先设置 HTML，再改状态，确保 v-html 在 v-if 生效时已有内容
    renderedHtml.value = md.render(normalizeMarkdown(content))
    status.value = 'idle'
    interpretCount.value = record.interpretCount || 0
    tokensUsed.value = record.tokensUsed
    modelName.value = record.modelName || ''
    currentRecordId.value = record.id
    errorMsg.value = ''
    nextTick(() => processMermaidDiagrams())
  } else if (record.status === '2') {
    // 失败的记录：展示错误信息
    streamContent.value = ''
    renderedHtml.value = ''
    status.value = 'error'
    interpretCount.value = record.interpretCount || 0
    tokensUsed.value = record.tokensUsed
    modelName.value = record.modelName || ''
    currentRecordId.value = record.id
    errorMsg.value = record.errorMsg || '解读失败'
  }
}

function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time)
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const day = date.getDate().toString().padStart(2, '0')
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}`
}

// -----------------------------------------------------------------------
// 导出功能
// -----------------------------------------------------------------------
async function handleExport(command: string) {
  if (!streamContent.value) {
    ElMessage.warning('没有可导出的内容')
    return
  }

  const title = props.articleTitle || '新闻解读'
  const exportOptions = {
    status: status.value,
    interpretCount: interpretCount.value,
    modelName: modelName.value,
    createTime: new Date().toLocaleString('zh-CN')
  }

  if (command === 'pdf') {
    const loading = ElLoading.service({
      lock: true,
      text: '正在生成 PDF...',
      background: 'rgba(0, 0, 0, 0.7)'
    })
    try {
      await exportToPdf(streamContent.value, title, exportOptions)
      ElMessage.success('PDF 导出成功')
    } catch (e) {
      console.error('PDF 导出失败:', e)
      ElMessage.error('PDF 导出失败，请重试')
    } finally {
      loading.close()
    }
  } else if (command === 'html') {
    try {
      exportToHtml(streamContent.value, title, exportOptions)
      ElMessage.success('HTML 导出成功')
    } catch (e) {
      console.error('HTML 导出失败:', e)
      ElMessage.error('HTML 导出失败，请重试')
    }
  }
}

// -----------------------------------------------------------------------
// Mermaid 流程图处理（在真实 DOM 中将 mermaid 代码块替换为 SVG）
// -----------------------------------------------------------------------
async function processMermaidDiagrams() {
  if (!contentRef.value) return
  const elements = contentRef.value.querySelectorAll('.mermaid')
  if (elements.length === 0) return

  for (const el of Array.from(elements)) {
    const htmlEl = el as HTMLElement
    if (htmlEl.querySelector('svg')) continue

    const rawCode = htmlEl.textContent || ''
    if (!rawCode.trim()) continue

    // 尝试修复 AI 输出的常见 mermaid 语法问题
    const code = fixMermaidSyntax(rawCode.trim())

    try {
      await mermaid.parse(code)
      const id = `mmd-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      const { svg } = await mermaid.render(id, code)
      const wrapper = htmlEl.closest('.mermaid-wrapper') || htmlEl.parentElement
      if (wrapper) {
        wrapper.innerHTML = svg
      } else {
        htmlEl.innerHTML = svg
      }
    } catch (e) {
      console.warn('Mermaid 渲染失败，降级为纯文本代码块:', (e as Error).message)
      // 渲染失败时降级为显示代码块，而不是隐藏
      const wrapper = htmlEl.closest('.mermaid-wrapper')
      if (wrapper) {
        const escaped = escapeHtml(rawCode)
        wrapper.outerHTML = `<div class="mermaid-fallback"><pre><code>${escaped}</code></pre></div>`
      }
    }
  }
}

/**
 * 修复 AI 输出的常见 mermaid 语法问题
 * - 未闭合的方括号：[文本 → [文本]
 * - 未闭合的花括号：{文本 → {文本}
 * - 未闭合的圆括号：(文本 → (文本)
 * - 未闭合的双引号
 */
function fixMermaidSyntax(code: string): string {
  return code
    .split('\n')
    .map(line => {
      let fixed = line
      // 修复未闭合的方括号 [...] — 统计 [ 和 ] 数量
      const openBrackets = (fixed.match(/\[/g) || []).length
      const closeBrackets = (fixed.match(/\]/g) || []).length
      if (openBrackets > closeBrackets) {
        fixed += ']'.repeat(openBrackets - closeBrackets)
      }
      // 修复未闭合的花括号 {...}
      const openBraces = (fixed.match(/\{/g) || []).length
      const closeBraces = (fixed.match(/\}/g) || []).length
      if (openBraces > closeBraces) {
        fixed += '}'.repeat(openBraces - closeBraces)
      }
      // 修复未闭合的圆括号 (...)
      const openParens = (fixed.match(/\(/g) || []).length
      const closeParens = (fixed.match(/\)/g) || []).length
      if (openParens > closeParens) {
        fixed += ')'.repeat(openParens - closeParens)
      }
      // 修复未闭合的双引号
      const quotes = (fixed.match(/"/g) || []).length
      if (quotes % 2 !== 0) {
        fixed += '"'
      }
      return fixed
    })
    .join('\n')
}

// -----------------------------------------------------------------------
// 工具函数
// -----------------------------------------------------------------------
function reset() {
  streamContent.value = ''
  renderedHtml.value = ''
  status.value = ''
  errorMsg.value = ''
  interpretCount.value = 0
  tokensUsed.value = undefined
  modelName.value = ''
  currentRecordId.value = undefined
}

function scrollToBottom() {
  if (contentRef.value) {
    contentRef.value.scrollTop = contentRef.value.scrollHeight
  }
}

function handleClose() {
  abortFn?.()
  abortFn = null
  emit('update:modelValue', false)
}

onUnmounted(() => {
  abortFn?.()
})
</script>

<style scoped>
/* 布局容器 */
.interpret-layout {
  display: flex;
  gap: 16px;
  min-height: 400px;
}
.interpret-main {
  flex: 1;
  min-width: 0;
}

/* 状态栏 */
.interpret-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 14px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 14px;
}
.status-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.status-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.interpret-count {
  font-size: 13px;
  color: #909399;
}
.interpret-status-tag {
  white-space: nowrap;
  font-size: 14px;
  padding: 0 12px;
}
.token-info {
  font-size: 12px;
  color: #b0b8c1;
}
.export-dropdown {
  display: inline-block;
}

/* 骨架屏 */
.interpret-skeleton {
  padding: 10px 0;
}

/* 内容区 */
.interpret-content {
  max-height: 60vh;
  overflow-y: auto;
  padding: 4px 6px;
  font-size: 14px;
  line-height: 1.8;
}
.stream-text {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
  color: #303133;
}
.typing-cursor {
  display: inline-block;
  color: #409eff;
  font-weight: bold;
  animation: blink 0.8s step-end infinite;
  margin-left: 1px;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0; }
}

/* 历史记录面板 */
.interpret-history {
  width: 260px;
  flex-shrink: 0;
  border-left: 1px solid #ebeef5;
  padding-left: 16px;
}
.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
}
.history-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.history-loading,
.history-empty {
  padding: 20px 0;
}
.history-list {
  max-height: 58vh;
  overflow-y: auto;
}
.history-item {
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.history-item:hover {
  border-color: #409eff;
  background: #f0f7ff;
}
.history-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.history-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.history-count {
  font-size: 12px;
  color: #909399;
}
.history-item-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #b0b8c1;
  margin-bottom: 4px;
}
.history-item-model {
  font-size: 11px;
  color: #c0c4cc;
}
.history-item-error {
  font-size: 11px;
  color: #f56c6c;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 历史记录面板滑入动画 */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>

<!-- Markdown 全局样式（非 scoped） -->
<style>
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  font-weight: 600;
  margin: 1em 0 0.5em;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 4px;
}
.markdown-body p {
  margin: 0.6em 0;
}
.markdown-body ul,
.markdown-body ol {
  padding-left: 1.8em;
  margin: 0.5em 0;
}
.markdown-body li {
  margin: 0.25em 0;
}
.markdown-body blockquote {
  border-left: 4px solid #dfe2e5;
  padding: 6px 12px;
  color: #6a737d;
  margin: 0.5em 0;
  background: #f8f9fa;
  border-radius: 0 4px 4px 0;
}
.markdown-body code {
  background: #f0f0f0;
  padding: 2px 5px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
}
.markdown-body pre {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 14px 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 0.8em 0;
}
.markdown-body pre code {
  background: transparent;
  padding: 0;
  color: inherit;
  font-size: 0.88em;
}
.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 0.8em 0;
}
.markdown-body th,
.markdown-body td {
  border: 1px solid #dfe2e5;
  padding: 6px 12px;
  text-align: left;
}
.markdown-body th {
  background: #f6f8fa;
  font-weight: 600;
}
.markdown-body tr:nth-child(even) td {
  background: #fafafa;
}
.mermaid-wrapper {
  overflow-x: auto;
  margin: 12px 0;
  padding: 10px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  text-align: center;
}
.mermaid svg {
  max-width: 100%;
  height: auto;
}
.mermaid-fallback {
  overflow-x: auto;
  margin: 12px 0;
  padding: 10px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.mermaid-fallback pre {
  background: transparent;
  color: #303133;
  padding: 0;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Courier New', monospace;
  font-size: 0.88em;
}
</style>
