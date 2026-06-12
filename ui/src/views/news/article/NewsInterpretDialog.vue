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
      <div class="interpret-main">
        <!-- 状态栏 -->
        <div class="interpret-status-bar">
          <div class="status-left">
            <el-tag v-if="status === 'running'" type="warning" effect="dark" class="interpret-status-tag">
              解读中
            </el-tag>
            <el-tag v-else-if="status === 'done'" type="success" effect="dark">解读完成</el-tag>
            <el-tag v-else-if="status === 'error'" type="danger" effect="dark">解读失败</el-tag>
            <span v-if="modelName && (status === 'done' || status === 'running')" class="model-info">{{ modelName }}</span>
          </div>
          <div class="status-right">
            <!-- 导出按钮 -->
            <el-dropdown
              v-if="status === 'done' && !compact"
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

            <!-- 重新解读 -->
            <el-button
              v-if="status === 'done' || status === 'error'"
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

        <!-- 内容展示区 -->
        <div
          v-if="displayContent"
          class="interpret-content"
          :class="status === 'running' ? 'stream-text' : 'markdown-body'"
          ref="contentRef"
          v-html="displayContent"
        ></div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="NewsInterpretDialog">
import { Download, Document, Monitor } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import mermaid from 'mermaid'
import { ElMessage, ElLoading } from 'element-plus'
import { startInterpret } from '@/api/news/interpretation'
import { exportToPdf, exportToHtml } from '@/utils/interpretExport'

// -----------------------------------------------------------------------
// Props & Emits
// -----------------------------------------------------------------------
const props = defineProps<{
  modelValue: boolean
  articleId: number | null
  articleTitle?: string
  /** 精简模式：隐藏导出按钮，打开直接开始解读 */
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

const defaultFence = md.renderer.rules.fence!.bind(md.renderer.rules)
md.renderer.rules.fence = (tokens: any[], idx: number, options: any, env: any, self: any) => {
  const token = tokens[idx]
  if (token.info.trim().startsWith('mermaid')) {
    const escaped = md.utils.escapeHtml(token.content)
    return `<div class="mermaid-wrapper"><pre><code class="language-mermaid mermaid">${escaped}</code></pre></div>`
  }
  return defaultFence(tokens, idx, options, env, self)
}

// -----------------------------------------------------------------------
// 状态
// -----------------------------------------------------------------------
type StatusType = '' | 'running' | 'done' | 'error'

const status = ref<StatusType>('')
const streamContent = ref('')
const errorMsg = ref('')
const modelName = ref('')
const contentRef = ref<HTMLDivElement>()

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

const displayContent = computed(() => {
  if (status.value === 'running' && streamContent.value) {
    return escapeHtml(streamContent.value) + '<span class="typing-cursor">|</span>'
  }
  if (status.value === 'done' && renderedHtml.value) {
    return renderedHtml.value
  }
  return ''
})

// -----------------------------------------------------------------------
// Markdown 格式化
// -----------------------------------------------------------------------
function normalizeMarkdown(text: string): string {
  let result = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  result = result
    .replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
    .replace(/^(##\s+.+?)(###\s*)/gm, '$1\n### ')
    .replace(/^([*-])([^\s*-])/gm, '$1 $2')
    .replace(/^(\d+\.)([^\s])/gm, '$1 $2')
    .replace(/^(>)([^\s>])/gm, '$1 $2')
    .replace(/([^\n])```mermaid/gi, '$1\n```mermaid')
    .replace(/^```mermaid(\S)/gmi, '```mermaid\n$1')
  result = autoWrapMermaid(result)
  return result
}

function autoWrapMermaid(text: string): string {
  if (/^```mermaid\b/mi.test(text)) return text

  const lines = text.split('\n')
  const mermaidLineRegex = /^[\s]*[A-Za-z0-9_\[\]()"'一-鿿]+\s*(-->|---|==>|-\.->)\s*/
  const graphDeclRegex = /^(graph\s+(TB|TD|BT|RL|LR)|flowchart\s+(TB|TD|BT|RL|LR))/i

  const segments: Array<{ start: number; end: number }> = []
  let i = 0
  while (i < lines.length) {
    if (/^```/.test(lines[i].trim())) {
      i++
      while (i < lines.length && !/^```/.test(lines[i].trim())) i++
      i++
      continue
    }
    const isGraphDecl = graphDeclRegex.test(lines[i].trim())
    const isMermaidLine = mermaidLineRegex.test(lines[i])
    if (isGraphDecl || isMermaidLine) {
      const start = i
      i++
      while (i < lines.length) {
        const trimmed = lines[i].trim()
        if (trimmed === '' || mermaidLineRegex.test(trimmed)) i++
        else break
      }
      const nonEmptyCount = lines.slice(start, i).filter(l => l.trim() !== '').length
      if (nonEmptyCount >= 2) segments.push({ start, end: i })
    } else {
      i++
    }
  }
  if (segments.length === 0) return text

  const result = [...lines]
  for (let s = segments.length - 1; s >= 0; s--) {
    const seg = segments[s]
    const blockLines = result.slice(seg.start, seg.end)
    while (blockLines.length > 0 && blockLines[0].trim() === '') blockLines.shift()
    while (blockLines.length > 0 && blockLines[blockLines.length - 1].trim() === '') blockLines.pop()
    const hasGraphDecl = graphDeclRegex.test(blockLines[0].trim())
    const wrapped = ['```mermaid', hasGraphDecl ? '' : 'flowchart TD', ...blockLines, '```']
    result.splice(seg.start, seg.end - seg.start, ...wrapped.filter(l => l !== '').join('\n'))
  }
  return result.join('\n')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

// -----------------------------------------------------------------------
// 打开对话框时自动开始解读
// -----------------------------------------------------------------------
watch(() => props.modelValue, (val: boolean) => {
  if (val && props.articleId) {
    doStartInterpret()
  }
})

function doStartInterpret() {
  reset()
  status.value = 'running'

  abortFn = startInterpret(props.articleId!, {
    onChunk(text) {
      streamContent.value += text
      nextTick(() => scrollToBottom())
    },
    onDone(data) {
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
// 导出
// -----------------------------------------------------------------------
async function handleExport(command: string) {
  if (!streamContent.value) {
    ElMessage.warning('没有可导出的内容')
    return
  }
  const title = props.articleTitle || '新闻解读'
  const exportOptions = {
    modelName: modelName.value,
    createTime: new Date().toLocaleString('zh-CN')
  }
  if (command === 'pdf') {
    const loading = ElLoading.service({ lock: true, text: '正在生成 PDF...', background: 'rgba(0, 0, 0, 0.7)' })
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
// Mermaid 渲染
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
      const wrapper = htmlEl.closest('.mermaid-wrapper')
      if (wrapper) {
        const escaped = escapeHtml(rawCode)
        wrapper.outerHTML = `<div class="mermaid-fallback"><pre><code>${escaped}</code></pre></div>`
      }
    }
  }
}

function fixMermaidSyntax(code: string): string {
  return code
    .split('\n')
    .map(line => {
      let fixed = line
      const openBrackets = (fixed.match(/\[/g) || []).length
      const closeBrackets = (fixed.match(/\]/g) || []).length
      if (openBrackets > closeBrackets) fixed += ']'.repeat(openBrackets - closeBrackets)
      const openBraces = (fixed.match(/\{/g) || []).length
      const closeBraces = (fixed.match(/\}/g) || []).length
      if (openBraces > closeBraces) fixed += '}'.repeat(openBraces - closeBraces)
      const openParens = (fixed.match(/\(/g) || []).length
      const closeParens = (fixed.match(/\)/g) || []).length
      if (openParens > closeParens) fixed += ')'.repeat(openParens - closeParens)
      const quotes = (fixed.match(/"/g) || []).length
      if (quotes % 2 !== 0) fixed += '"'
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
  modelName.value = ''
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
.interpret-layout {
  display: flex;
  min-height: 400px;
}
.interpret-main {
  flex: 1;
  min-width: 0;
}

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
.interpret-status-tag {
  white-space: nowrap;
  font-size: 14px;
  padding: 0 12px;
}
.model-info {
  font-size: 12px;
  color: #909399;
}
.export-dropdown {
  display: inline-block;
}

.interpret-skeleton {
  padding: 10px 0;
}

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
</style>

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
