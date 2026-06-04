<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="类型名称" prop="typeName">
        <el-input v-model="queryParams.typeName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['news:type:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['news:type:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" prop="id" width="80" align="center" />
      <el-table-column label="类型名称" prop="typeName" :show-overflow-tooltip="true" />
      <el-table-column label="编码" prop="typeCode" width="120" />
      <el-table-column label="描述" prop="description" :show-overflow-tooltip="true" />
      <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
      <el-table-column label="状态" prop="isActive" width="80" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isActive === 1 ? 'success' : 'danger'" size="small">{{ scope.row.isActive === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['news:type:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['news:type:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="类型名称" prop="typeName"><el-input v-model="form.typeName" placeholder="如：AI科技" /></el-form-item>
        <el-form-item label="编码" prop="typeCode"><el-input v-model="form.typeCode" placeholder="如：ai_tech" /></el-form-item>
        <el-form-item label="描述" prop="description"><el-input v-model="form.description" type="textarea" placeholder="类型描述" /></el-form-item>
        <el-form-item label="排序" prop="sortOrder"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.isActive"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="submitForm">确定</el-button><el-button @click="cancel">取消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NewsTypeConfig">
import { listType, getType, addType, updateType, delType } from '@/api/news/typeConfig'
import type { NewsTypeConfig, NewsTypeConfigQueryParams } from '@/types/api/news/typeConfig'
const { proxy } = getCurrentInstance()
const list = ref<NewsTypeConfig[]>([]); const open = ref(false); const loading = ref(true); const showSearch = ref(true)
const ids = ref<number[]>([]); const single = ref(true); const multiple = ref(true); const total = ref(0); const title = ref('')
const data = reactive({ form: {} as NewsTypeConfig, queryParams: { pageNum: 1, pageSize: 10 } as NewsTypeConfigQueryParams,
  rules: { typeName: [{ required: true, message: '类型名称不能为空', trigger: 'blur' }], typeCode: [{ required: true, message: '编码不能为空', trigger: 'blur' }] } })
const { queryParams, form, rules } = toRefs(data)

function getList() { loading.value = true; listType(queryParams.value).then(r => { list.value = r.rows; total.value = r.total; loading.value = false }) }
function cancel() { open.value = false; reset() }
function reset() { form.value = { typeName: undefined, typeCode: undefined, isActive: 1, sortOrder: 0 } as NewsTypeConfig; proxy?.resetForm('formRef') }
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy?.resetForm('queryRef'); handleQuery() }
function handleSelectionChange(s: NewsTypeConfig[]) { ids.value = s.map(i => i.id as number); single.value = s.length !== 1; multiple.value = !s.length }
function handleAdd() { reset(); open.value = true; title.value = '新增新闻类型' }
function handleUpdate(row?: NewsTypeConfig) { reset(); getType(row?.id || ids.value[0]).then(r => { form.value = r.data; open.value = true; title.value = '修改新闻类型' }) }
function submitForm() { (proxy?.$refs['formRef'] as any)?.validate((v: boolean) => { if (v) { const fn = form.value.id ? updateType : addType; fn(form.value).then(() => { proxy?.$modal.msgSuccess('操作成功'); open.value = false; getList() }) } }) }
function handleDelete(row?: NewsTypeConfig) { proxy?.$modal.confirm('确认删除？').then(() => delType(row?.id ? [row.id] : ids.value)).then(() => { getList(); proxy?.$modal.msgSuccess('删除成功') }).catch(() => {}) }
getList()
</script>
