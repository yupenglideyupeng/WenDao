<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="关键词" prop="text">
        <el-input v-model="queryParams.text" placeholder="请输入关键词" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-input v-model="queryParams.category" placeholder="请输入分类" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['news:keyword:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['news:keyword:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['news:keyword:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="keywordList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="关键词" align="center" prop="text" :show-overflow-tooltip="true" />
      <el-table-column label="分类" align="center" prop="category" width="120" />
      <el-table-column label="状态" align="center" prop="isActive" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.isActive === 1 ? 'success' : 'danger'" size="small">
            {{ scope.row.isActive === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="间隔(分钟)" align="center" prop="fetchInterval" width="100" />
      <el-table-column label="相关性阈值" align="center" prop="relevanceThreshold" width="100">
        <template #default="scope">
          <span>{{ scope.row.relevanceThreshold ?? 60 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上次抓取" align="center" prop="lastFetchTime" width="180">
        <template #default="scope">
          <span>{{ scope.row.lastFetchTime || '未抓取' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['news:keyword:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['news:keyword:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="keywordRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="关键词" prop="text">
          <el-input v-model="form.text" placeholder="请输入监控关键词" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" placeholder="请输入分类（可选）" />
        </el-form-item>
        <el-form-item label="抓取间隔" prop="fetchInterval">
          <el-input-number v-model="form.fetchInterval" :min="5" :max="1440" placeholder="分钟" />
        </el-form-item>
        <el-form-item label="相关性阈值" prop="relevanceThreshold">
          <el-input-number v-model="form.relevanceThreshold" :min="0" :max="100" placeholder="0-100" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">低于此值的文章自动下架</span>
        </el-form-item>
        <el-form-item label="状态" prop="isActive">
          <el-radio-group v-model="form.isActive">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NewsKeyword">
import { listKeyword, getKeyword, addKeyword, updateKeyword, delKeyword } from '@/api/news/keyword'
import type { NewsKeyword, NewsKeywordQueryParams } from '@/types/api/news/keyword'

const { proxy } = getCurrentInstance()

const keywordList = ref<NewsKeyword[]>([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const data = reactive({
  form: {} as NewsKeyword,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    text: undefined,
    category: undefined
  } as NewsKeywordQueryParams,
  rules: {
    text: [{ required: true, message: '关键词不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listKeyword(queryParams.value).then(response => {
    keywordList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    id: undefined,
    text: undefined,
    category: undefined,
    isActive: 1,
    fetchInterval: 30,
    relevanceThreshold: 60
  } as NewsKeyword
  proxy?.resetForm('keywordRef')
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy?.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection: NewsKeyword[]) {
  ids.value = selection.map(item => item.id as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '添加关键词'
}

function handleUpdate(row?: NewsKeyword) {
  reset()
  const id = row?.id || ids.value[0]
  getKeyword(id).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改关键词'
  })
}

function submitForm() {
  (proxy?.$refs['keywordRef'] as any)?.validate((valid: boolean) => {
    if (valid) {
      if (form.value.id != null) {
        updateKeyword(form.value).then(() => {
          proxy?.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addKeyword(form.value).then(() => {
          proxy?.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row?: NewsKeyword) {
  const delIds = row?.id ? [row.id] : ids.value
  proxy?.$modal.confirm('是否确认删除所选关键词？').then(function () {
    return delKeyword(delIds as number[])
  }).then(() => {
    getList()
    proxy?.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
