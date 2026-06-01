<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="来源名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入来源名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="queryParams.type" placeholder="请选择类型" clearable>
          <el-option v-for="dict in news_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['news:source:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['news:source:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['news:source:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="sourceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="来源名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" prop="type" width="100">
        <template #default="scope">
          <dict-tag :options="news_type" :value="scope.row.type" />
        </template>
      </el-table-column>
      <el-table-column label="抓取方式" align="center" prop="fetchType" width="100" />
      <el-table-column label="间隔(分钟)" align="center" prop="fetchInterval" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['news:source:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['news:source:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="sourceRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="来源名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入来源名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型">
            <el-option v-for="dict in news_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="新闻源地址" prop="url">
          <el-input v-model="form.url" placeholder="请输入新闻源地址" type="textarea" />
        </el-form-item>
        <el-form-item label="抓取方式" prop="fetchType">
          <el-select v-model="form.fetchType" placeholder="请选择抓取方式">
            <el-option label="RSS" value="RSS" />
            <el-option label="API" value="API" />
            <el-option label="爬虫" value="CRAWL" />
          </el-select>
        </el-form-item>
        <el-form-item label="抓取间隔" prop="fetchInterval">
          <el-input-number v-model="form.fetchInterval" :min="1" :max="1440" placeholder="分钟" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
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

<script setup lang="ts" name="NewsSource">
import { listSource, getSource, addSource, updateSource, delSource } from '@/api/news/source'
import type { NewsSource, NewsSourceQueryParams } from '@/types/api/news/source'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict('sys_normal_disable')

// 新闻类型字典
const news_type = ref([
  { label: '国内', value: '0' },
  { label: '国外', value: '1' }
])

const sourceList = ref<NewsSource[]>([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const data = reactive({
  form: {} as NewsSource,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    type: undefined,
    status: undefined
  } as NewsSourceQueryParams,
  rules: {
    name: [{ required: true, message: '来源名称不能为空', trigger: 'blur' }],
    url: [{ required: true, message: '新闻源地址不能为空', trigger: 'blur' }],
    type: [{ required: true, message: '类型不能为空', trigger: 'change' }],
    fetchType: [{ required: true, message: '抓取方式不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listSource(queryParams.value).then(response => {
    sourceList.value = response.rows
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
    name: undefined,
    type: '0',
    url: undefined,
    fetchType: 'RSS',
    fetchInterval: 30,
    status: '0'
  } as NewsSource
  proxy?.resetForm('sourceRef')
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy?.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection: NewsSource[]) {
  ids.value = selection.map(item => item.id as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '添加新闻来源'
}

function handleUpdate(row?: NewsSource) {
  reset()
  const id = row?.id || ids.value[0]
  getSource(id).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改新闻来源'
  })
}

function submitForm() {
  (proxy?.$refs['sourceRef'] as any)?.validate((valid: boolean) => {
    if (valid) {
      if (form.value.id != null) {
        updateSource(form.value).then(() => {
          proxy?.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addSource(form.value).then(() => {
          proxy?.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row?: NewsSource) {
  const delIds = row?.id ? [row.id] : ids.value
  proxy?.$modal.confirm('是否确认删除所选新闻来源？').then(function () {
    return delSource(delIds as number[])
  }).then(() => {
    getList()
    proxy?.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
