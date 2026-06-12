import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'
import mermaid from 'mermaid'
import MarkdownIt from 'markdown-it'

// Markdown 渲染器（与 Dialog 中保持一致）
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

// 自定义 fence 渲染：将 ```mermaid 代码块转为 mermaid div
const mermaidIdCounter = { count: 0 }
md.renderer.rules.fence = (tokens: any[], idx: number, options: any, env: any, self: any) => {
  const token = tokens[idx]
  if (token.info.trim().startsWith('mermaid')) {
    const id = `mermaid-export-${mermaidIdCounter.count++}`
    return `<div class="mermaid" id="${id}">${token.content}</div>`
  }
  return self.renderToken(tokens, idx, options)
}

/**
 * 修复 AI 输出的 Markdown 格式问题（与 Dialog 中 normalizeMarkdown 保持一致）
 */
function normalizeMarkdown(text: string): string {
  let result = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  result = result
    .replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
    .replace(/^(##\s+.+?)(###\s*)/gm, '$1\n### ')
    .replace(/^([*-])([^\s*-])/gm, '$1 $2')
    .replace(/^(\d+\.)([^\s])/gm, '$1 $2')
    .replace(/^(>)([^\s>])/gm, '$1 $2')
    // 修复 AI 输出中 ```mermaid 不在独立行的问题
    // 情况A：```mermaid 在行中（如：影响评估```mermaidgraph TD）
    //   → 确保 ```mermaid 前有换行，但保留标记本身
    // 情况B：```mermaid 在行首但代码粘连（如：```mermaidgraph TD）
    //   → 在 mermaid 和代码之间插入换行
    .replace(/([^\n])```mermaid/gi, '$1\n```mermaid')
    .replace(/^```mermaid(\S)/gmi, '```mermaid\n$1')
  result = autoWrapMermaid(result)
  return result
}

/**
 * 自动检测并包裹未用 ```mermaid 包裹的 mermaid 流程图语法
 */
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
    if (!graphDeclRegex.test(blockLines[0].trim())) {
      blockLines.unshift('flowchart TD')
    }
    const wrapped = ['```mermaid', ...blockLines, '```']
    result.splice(seg.start, seg.end - seg.start, ...wrapped)
  }
  return result.join('\n')
}

/**
 * 导出为 PDF
 * 将解读内容（含 Mermaid 流程图）渲染为 PDF 文件
 */
export async function exportToPdf(
  content: string,
  title: string,
  options?: {
    modelName?: string
    createTime?: string
  }
): Promise<void> {
  // 1. 创建临时容器用于渲染（使用可见位置，确保字体正确加载）
  const container = document.createElement('div')
  container.style.cssText = `
    position: fixed;
    left: 0;
    top: 0;
    width: 800px;
    padding: 40px;
    background: white;
    color: #303133;
    font-family: "Microsoft YaHei", "PingFang SC", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 14px;
    line-height: 1.8;
    z-index: -9999;
    opacity: 0;
  `
  document.body.appendChild(container)

  // 2. 构建 HTML 内容
  const headerHtml = `
    <div style="border-bottom: 2px solid #409eff; padding-bottom: 16px; margin-bottom: 24px;">
      <h1 style="margin: 0 0 12px 0; font-size: 24px; color: #303133;">${escapeHtml(title)}</h1>
      <div style="font-size: 13px; color: #909399;">
        ${options?.modelName ? `<span style="margin-left: 16px;">模型：${escapeHtml(options.modelName)}</span>` : ''}
        ${options?.createTime ? `<span style="margin-left: 16px;">时间：${escapeHtml(options.createTime)}</span>` : ''}
      </div>
    </div>
  `

  // 3. 渲染 Markdown 内容
  const markdownHtml = md.render(normalizeMarkdown(content))
  container.innerHTML = headerHtml + `<div class="markdown-body" style="color: #303133;">${markdownHtml}</div>`

  // 4. 添加样式（确保所有文字颜色明确）
  const style = document.createElement('style')
  style.textContent = `
    * { color: #303133; }
    h1, h2, h3, h4 { color: #303133 !important; }
    p, li, td, th, span, div { color: #303133 !important; }
    a { color: #409eff !important; }
    .markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4 {
      font-weight: 600;
      margin: 1em 0 0.5em;
      border-bottom: 1px solid #ebeef5;
      padding-bottom: 4px;
      color: #303133 !important;
    }
    .markdown-body p { margin: 0.6em 0; color: #303133 !important; }
    .markdown-body ul, .markdown-body ol { padding-left: 1.8em; margin: 0.5em 0; }
    .markdown-body li { margin: 0.25em 0; color: #303133 !important; }
    .markdown-body blockquote {
      border-left: 4px solid #dfe2e5;
      padding: 6px 12px;
      color: #6a737d !important;
      margin: 0.5em 0;
      background: #f8f9fa;
    }
    .markdown-body code {
      background: #f0f0f0;
      padding: 2px 5px;
      border-radius: 3px;
      font-family: 'Courier New', monospace;
      font-size: 0.9em;
      color: #c7254e !important;
    }
    .markdown-body pre {
      background: #1e1e1e;
      color: #d4d4d4 !important;
      padding: 14px 16px;
      border-radius: 6px;
      overflow-x: auto;
      margin: 0.8em 0;
    }
    .markdown-body pre code {
      background: transparent;
      padding: 0;
      color: #d4d4d4 !important;
    }
    .markdown-body table {
      border-collapse: collapse;
      width: 100%;
      margin: 0.8em 0;
    }
    .markdown-body th, .markdown-body td {
      border: 1px solid #dfe2e5;
      padding: 6px 12px;
      text-align: left;
      color: #303133 !important;
    }
    .markdown-body th {
      background: #f6f8fa;
      font-weight: 600;
    }
    .mermaid {
      text-align: center;
      margin: 16px 0;
      padding: 12px;
      background: #fafafa;
      border-radius: 6px;
      border: 1px solid #ebeef5;
    }
    .mermaid svg {
      max-width: 100%;
      height: auto;
    }
  `
  container.insertBefore(style, container.firstChild)

  // 5. 渲染 Mermaid 图表（逐个处理，语法错误的隐藏）
  const mermaidElements = container.querySelectorAll('.mermaid')
  if (mermaidElements.length > 0) {
    for (const el of Array.from(mermaidElements)) {
      const htmlEl = el as HTMLElement
      const graphDef = htmlEl.textContent || ''
      try {
        await mermaid.parse(graphDef)
        const id = `mermaid-pdf-${Date.now()}-${Math.random().toString(36).slice(2)}`
        const { svg } = await mermaid.render(id, graphDef)
        htmlEl.innerHTML = svg
      } catch (e) {
        console.warn('Mermaid 语法错误，跳过此流程图:', e)
        htmlEl.style.display = 'none'
      }
    }
  }

  // 6. 等待字体和渲染完成
  await new Promise(resolve => setTimeout(resolve, 300))

  // 7. 使用 html2canvas 截图
  const canvas = await html2canvas(container, {
    scale: 2,
    useCORS: true,
    logging: false,
    backgroundColor: '#ffffff',
    allowTaint: true,
    foreignObjectRendering: true
  })

  // 8. 生成 PDF
  const imgWidth = 210 // A4 宽度 (mm)
  const pageHeight = 297 // A4 高度 (mm)
  const imgHeight = (canvas.height * imgWidth) / canvas.width
  const pdf = new jsPDF('p', 'mm', 'a4')

  let heightLeft = imgHeight
  let position = 0

  // 第一页
  pdf.addImage(canvas.toDataURL('image/png'), 'PNG', 0, position, imgWidth, imgHeight)
  heightLeft -= pageHeight

  // 如果内容超过一页，添加分页
  while (heightLeft > 0) {
    position = heightLeft - imgHeight
    pdf.addPage()
    pdf.addImage(canvas.toDataURL('image/png'), 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pageHeight
  }

  // 9. 下载 PDF
  const fileName = `${title}_解读_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.pdf`
  pdf.save(fileName)

  // 10. 清理临时容器
  document.body.removeChild(container)
}

/**
 * 导出为 HTML
 * 生成独立 HTML 文件，包含 Mermaid CDN 脚本，可在浏览器中打开
 */
export function exportToHtml(
  content: string,
  title: string,
  options?: {
    modelName?: string
    createTime?: string
  }
): void {
  // 渲染 Markdown 内容
  const markdownHtml = md.render(normalizeMarkdown(content))

  // 构建完整 HTML
  const htmlContent = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escapeHtml(title)} - AI 解读</title>
  <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"><\/script>
  <style>
    * { box-sizing: border-box; }
    body {
      font-family: "Microsoft YaHei", "PingFang SC", -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
      max-width: 900px;
      margin: 0 auto;
      padding: 40px 20px;
      background: #fafafa;
      color: #303133;
      line-height: 1.8;
    }
    .header {
      border-bottom: 2px solid #409eff;
      padding-bottom: 20px;
      margin-bottom: 30px;
    }
    .header h1 {
      margin: 0 0 12px 0;
      font-size: 28px;
      color: #303133;
    }
    .meta {
      font-size: 14px;
      color: #909399;
    }
    .meta span {
      margin-right: 16px;
    }
    .markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4 {
      font-weight: 600;
      margin: 1.2em 0 0.6em;
      border-bottom: 1px solid #ebeef5;
      padding-bottom: 6px;
    }
    .markdown-body p { margin: 0.8em 0; }
    .markdown-body ul, .markdown-body ol { padding-left: 2em; margin: 0.6em 0; }
    .markdown-body li { margin: 0.3em 0; }
    .markdown-body blockquote {
      border-left: 4px solid #dfe2e5;
      padding: 10px 16px;
      color: #6a737d;
      margin: 1em 0;
      background: #f8f9fa;
      border-radius: 0 4px 4px 0;
    }
    .markdown-body code {
      background: #f0f0f0;
      padding: 2px 6px;
      border-radius: 4px;
      font-family: 'Courier New', monospace;
      font-size: 0.9em;
    }
    .markdown-body pre {
      background: #1e1e1e;
      color: #d4d4d4;
      padding: 16px 20px;
      border-radius: 8px;
      overflow-x: auto;
      margin: 1em 0;
    }
    .markdown-body pre code {
      background: transparent;
      padding: 0;
      color: inherit;
    }
    .markdown-body table {
      border-collapse: collapse;
      width: 100%;
      margin: 1em 0;
    }
    .markdown-body th, .markdown-body td {
      border: 1px solid #dfe2e5;
      padding: 10px 14px;
      text-align: left;
    }
    .markdown-body th {
      background: #f6f8fa;
      font-weight: 600;
    }
    .mermaid {
      text-align: center;
      margin: 20px 0;
      padding: 16px;
      background: white;
      border-radius: 8px;
      border: 1px solid #ebeef5;
      box-shadow: 0 2px 8px rgba(0,0,0,0.04);
    }
    .mermaid svg {
      max-width: 100%;
      height: auto;
    }
    .footer {
      margin-top: 40px;
      padding-top: 20px;
      border-top: 1px solid #ebeef5;
      font-size: 12px;
      color: #b0b8c1;
      text-align: center;
    }
    @media print {
      body { background: white; }
    }
  </style>
</head>
<body>
  <div class="header">
    <h1>${escapeHtml(title)}</h1>
    <div class="meta">
      ${options?.modelName ? `<span>模型：${escapeHtml(options.modelName)}</span>` : ''}
      ${options?.createTime ? `<span>时间：${escapeHtml(options.createTime)}</span>` : ''}
    </div>
  </div>

  <div class="markdown-body">
    ${markdownHtml}
  </div>

  <div class="footer">
    由闻道管理系统 AI 一键解读生成
  </div>

  <script>
    mermaid.initialize({
      startOnLoad: true,
      theme: 'default',
      securityLevel: 'loose',
      fontFamily: 'inherit'
    });
  <\/script>
</body>
</html>`

  // 下载 HTML 文件
  const blob = new Blob([htmlContent], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${title}_解读_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.html`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * HTML 转义
 */
function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
