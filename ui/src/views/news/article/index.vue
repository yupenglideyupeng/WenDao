<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="文章标题" prop="title">
        <el-input v-model="queryParams.title" placeholder="请输入文章标题" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="新闻类型" prop="typeConfigId">
        <el-select v-model="queryParams.typeConfigId" placeholder="请选择" clearable>
          <el-option v-for="t in typeOptions" :key="t.id" :label="t.typeName" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="语言" prop="language">
        <el-select v-model="queryParams.language" placeholder="请选择语言" clearable>
          <el-option label="中文" value="zh" />
          <el-option label="英文" value="en" />
        </el-select>
      </el-form-item>
      <el-form-item label="情感" prop="sentiment">
        <el-select v-model="queryParams.sentiment" placeholder="请选择情感" clearable>
          <el-option label="积极" value="positive" />
          <el-option label="中性" value="neutral" />
          <el-option label="消极" value="negative" />
        </el-select>
      </el-form-item>
      <el-form-item label="推送状态" prop="isPushed">
        <el-select v-model="queryParams.isPushed" placeholder="请选择" clearable>
          <el-option label="已推送" value="1" />
          <el-option label="未推送" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源方式" prop="fetchOrigin">
        <el-select v-model="queryParams.fetchOrigin" placeholder="请选择" clearable>
          <el-option label="关键词" value="KEYWORD" />
          <el-option label="来源" value="SOURCE" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['news:article:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="articleList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="文章标题" align="center" prop="title" :show-overflow-tooltip="true" min-width="200">
        <template #default="scope">
          <el-link type="primary" :href="scope.row.originalUrl" target="_blank" :underline="false">
            {{ scope.row.title }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" prop="sourceName" width="120" />
      <el-table-column label="来源方式" align="center" prop="fetchOrigin" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.fetchOrigin === 'KEYWORD' ? '' : 'info'" size="small">
            {{ scope.row.fetchOrigin === 'KEYWORD' ? '关键词' : '来源' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="相关性" align="center" prop="relevance" width="90">
        <template #default="scope">
          <span v-if="scope.row.relevance != null && scope.row.relevance > 0"
                :style="{ color: scope.row.relevance >= 70 ? '#67C23A' : scope.row.relevance >= 40 ? '#E6A23C' : '#F56C6C' }">
            {{ scope.row.relevance }}
          </span>
          <span v-else style="color:#C0C4CC">-</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" align="center" prop="typeName" width="100">
        <template #default="scope">
          <el-tag size="small" v-if="scope.row.typeName">{{ scope.row.typeName }}</el-tag>
          <span v-else style="color:#909399;font-size:12px">-</span>
        </template>
      </el-table-column>
      <el-table-column label="语言" align="center" prop="language" width="70">
        <template #default="scope">
          <el-tag :type="scope.row.language === 'zh' ? '' : 'info'" size="small">
            {{ scope.row.language === 'zh' ? '中文' : scope.row.language === 'en' ? '英文' : scope.row.language }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="情感" align="center" prop="sentiment" width="80">
        <template #default="scope">
          <el-tag :type="sentimentType(scope.row.sentiment)" size="small" v-if="scope.row.sentiment">
            {{ sentimentLabel(scope.row.sentiment) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="推送状态" align="center" prop="isPushed" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.isPushed === '1' ? 'success' : 'warning'" size="small">
            {{ scope.row.isPushed === '1' ? '已推送' : '未推送' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" align="center" prop="publishTime" width="180" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="230">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button link type="warning" icon="MagicStick" @click="handleInterpret(scope.row)">解读</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['news:article:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 详情对话框 -->
    <el-dialog :title="detailTitle" v-model="detailOpen" width="700px" append-to-body>
      <div v-if="detail" class="article-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.sourceName }}</el-descriptions-item>
          <el-descriptions-item label="来源方式">
            <el-tag :type="detail.fetchOrigin === 'KEYWORD' ? '' : 'info'" size="small">
              {{ detail.fetchOrigin === 'KEYWORD' ? '关键词' : '来源' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="新闻类型">
            <el-tag size="small" v-if="detail.typeName">{{ detail.typeName }}</el-tag>
            <span v-else style="color:#909399">未分类</span>
          </el-descriptions-item>
          <el-descriptions-item label="语言">{{ detail.language === 'zh' ? '中文' : detail.language === 'en' ? '英文' : detail.language }}</el-descriptions-item>
          <el-descriptions-item label="情感">
            <el-tag :type="sentimentType(detail.sentiment)" size="small" v-if="detail.sentiment">{{ sentimentLabel(detail.sentiment) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="相关性">
            <span v-if="detail.relevance != null && detail.relevance > 0"
                  :style="{ color: detail.relevance >= 70 ? '#67C23A' : detail.relevance >= 40 ? '#E6A23C' : '#F56C6C', fontWeight: 'bold' }">
              {{ detail.relevance }}分
            </span>
            <span v-else style="color:#C0C4CC">未评分</span>
          </el-descriptions-item>
          <el-descriptions-item label="推送状态">{{ detail.isPushed === '1' ? '已推送' : '未推送' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <el-tag v-for="tag in parseTags(detail.tags)" :key="tag" size="small" style="margin-right: 5px;">{{ tag }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关键词" :span="2">{{ detail.keywords }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ detail.publishTime }}</el-descriptions-item>
          <el-descriptions-item label="原文链接">
            <el-link type="primary" :href="detail.originalUrl" target="_blank">查看原文</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="AI摘要" :span="2">{{ detail.summary }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 一键解读对话框 -->
    <NewsInterpretDialog
      v-model="interpretOpen"
      :article-id="interpretArticleId"
      :article-title="interpretArticleTitle"
    />
  </div>
</template>

<script setup lang="ts" name="NewsArticle">
import { listArticle, getArticle, delArticle } from '@/api/news/article'
import { listType } from '@/api/news/typeConfig'
import type { NewsArticle, NewsArticleQueryParams } from '@/types/api/news/article'
import type { NewsTypeConfig } from '@/types/api/news/typeConfig'
import NewsInterpretDialog from './NewsInterpretDialog.vue'

const { proxy } = getCurrentInstance()

const articleList = ref<NewsArticle[]>([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detailTitle = ref('')
const detail = ref<NewsArticle>()
const typeOptions = ref<NewsTypeConfig[]>([])

// 解读对话框状态
const interpretOpen = ref(false)
const interpretArticleId = ref<number | null>(null)
const interpretArticleTitle = ref('')

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    typeConfigId: undefined,
    language: undefined,
    sentiment: undefined,
    isPushed: undefined
  } as NewsArticleQueryParams
})

const { queryParams } = toRefs(data)

async function loadTypeOptions() {
  try {
    const r = await listType({ pageNum: 1, pageSize: 100, isActive: 1 })
    typeOptions.value = r.rows || []
  } catch (e) {}
}

function getList() {
  loading.value = true
  listArticle(queryParams.value).then(response => {
    articleList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy?.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection: NewsArticle[]) {
  ids.value = selection.map(item => item.id as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleDetail(row: NewsArticle) {
  getArticle(row.id!).then(response => {
    detail.value = response.data
    detailOpen.value = true
    detailTitle.value = '文章详情'
  })
}

function handleInterpret(row: NewsArticle) {
  interpretArticleId.value = row.id!
  interpretArticleTitle.value = row.title || ''
  interpretOpen.value = true
}

function handleDelete(row?: NewsArticle) {
  const delIds = row?.id ? [row.id] : ids.value
  proxy?.$modal.confirm('是否确认删除所选文章？').then(function () {
    return delArticle(delIds as number[])
  }).then(() => {
    getList()
    proxy?.$modal.msgSuccess('删除成功')
  }).catch(() => {})
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

function parseTags(tags?: string): string[] {
  if (!tags) return []
  try {
    return JSON.parse(tags)
  } catch {
    return tags.split(',').map(t => t.trim())
  }
}

loadTypeOptions()
getList()
</script>

<style scoped>
.article-detail {
  max-height: 500px;
  overflow-y: auto;
}
</style>
