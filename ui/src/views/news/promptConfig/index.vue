<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="提示词类型" prop="promptType" label-width="130">
        <el-select v-model="queryParams.promptType" placeholder="请选择" clearable>
          <el-option label="分析(ANALYSIS)" value="ANALYSIS" />
          <el-option label="解读(INTERPRET)" value="INTERPRET" />
          <el-option label="聚合(AGGREGATE)" value="AGGREGATE" />
          <el-option label="对比(COMPARE)" value="COMPARE" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['news:prompt:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['news:prompt:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" prop="id" width="80" align="center" />
      <el-table-column label="关联类型" prop="typeNames" min-width="150" :show-overflow-tooltip="true">
        <template #default="scope">
          <template v-if="scope.row.typeNames">
            <el-tag v-for="(name, idx) in scope.row.typeNames.split('，')" :key="idx" size="small" style="margin-right: 4px;">{{ name }}</el-tag>
          </template>
          <span v-else style="color:#909399">通用</span>
        </template>
      </el-table-column>
      <el-table-column label="提示词类型" prop="promptType" width="100">
        <template #default="scope">
          <el-tag size="small">{{ promptTypeLabel(scope.row.promptType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提示词" prop="systemPrompt" :show-overflow-tooltip="true" min-width="250" />
      <el-table-column label="温度" prop="temperature" width="80" align="center" />
      <el-table-column label="最大长度" prop="maxTokens" width="90" align="center" />
      <el-table-column label="状态" prop="isActive" width="80" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isActive === 1 ? 'success' : 'danger'" size="small">{{ scope.row.isActive === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['news:prompt:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['news:prompt:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="750px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="关联新闻类型" prop="typeConfigIds">
          <el-select v-model="form.typeConfigIds" multiple placeholder="可选择多个类型（留空=通用）" clearable style="width:100%">
            <el-option v-for="t in typeOptions" :key="t.id" :label="t.typeName" :value="t.id" />
          </el-select>
          <div style="color:#909399;font-size:12px;margin-top:4px">可选择多个新闻类型，留空表示适用于所有类型</div>
        </el-form-item>
        <el-form-item label="提示词类型" prop="promptType">
          <el-select v-model="form.promptType" placeholder="请选择">
            <el-option label="ANALYSIS-分析" value="ANALYSIS" />
            <el-option label="INTERPRET-解读" value="INTERPRET" />
            <el-option label="AGGREGATE-聚合" value="AGGREGATE" />
            <el-option label="COMPARE-对比" value="COMPARE" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="8" placeholder="输入系统提示词，告诉AI如何分析新闻..." />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" />
          <el-tooltip content="控制AI输出随机性：0~0.3=稳定确定(适合分析分类)，0.5~0.7=平衡，0.8~1.5=更有创意，1.5~2.0=非常随机" placement="top">
            <el-icon style="margin-left:8px;color:#909399;cursor:help"><QuestionFilled /></el-icon>
          </el-tooltip>
          <div style="color:#909399;font-size:12px">控制输出随机性，分析类建议 0.1~0.5</div>
        </el-form-item>
        <el-form-item label="最大长度">
          <el-input-number v-model="form.maxTokens" :min="100" :max="8000" :step="100" />
          <el-tooltip content="限制AI单次输出最大token数。1个中文≈1.5token，500≈300字，2000≈1200字。不影响计费上限" placement="top">
            <el-icon style="margin-left:8px;color:#909399;cursor:help"><QuestionFilled /></el-icon>
          </el-tooltip>
          <div style="color:#909399;font-size:12px">限制输出长度，500≈300字，2000≈1200字</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.isActive">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="submitForm">确定</el-button><el-button @click="cancel">取消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NewsPromptConfig">
import { QuestionFilled } from '@element-plus/icons-vue'
import { listPrompt, getPrompt, addPrompt, updatePrompt, delPrompt } from '@/api/news/promptConfig'
import { listType } from '@/api/news/typeConfig'
import type { NewsPromptConfig, NewsPromptConfigQueryParams } from '@/types/api/news/promptConfig'
import type { NewsTypeConfig } from '@/types/api/news/typeConfig'
const { proxy } = getCurrentInstance()
const list = ref<NewsPromptConfig[]>([]); const open = ref(false); const loading = ref(true); const showSearch = ref(true)
const ids = ref<number[]>([]); const single = ref(true); const multiple = ref(true); const total = ref(0); const title = ref('')
const typeOptions = ref<NewsTypeConfig[]>([])
const data = reactive({
  form: {} as NewsPromptConfig,
  queryParams: { pageNum: 1, pageSize: 10 } as NewsPromptConfigQueryParams,
  rules: {
    promptType: [{ required: true, message: '不能为空', trigger: 'change' }],
    systemPrompt: [{ required: true, message: '不能为空', trigger: 'blur' }]
  }
})
const { queryParams, form, rules } = toRefs(data)

function promptTypeLabel(v?: string) { const m: Record<string,string> = { ANALYSIS:'分析', INTERPRET:'解读', AGGREGATE:'聚合', COMPARE:'对比' }; return m[v||''] || v }
function getList() { loading.value = true; listPrompt(queryParams.value).then(r => { list.value = r.rows; total.value = r.total; loading.value = false }) }
function cancel() { open.value = false; reset() }
function reset() { form.value = { typeConfigIds: [], promptType: 'ANALYSIS', isActive: 1, temperature: 0.3, maxTokens: 500 } as NewsPromptConfig; proxy?.resetForm('formRef') }
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy?.resetForm('queryRef'); handleQuery() }
function handleSelectionChange(s: NewsPromptConfig[]) { ids.value = s.map(i => i.id as number); single.value = s.length !== 1; multiple.value = !s.length }
async function handleAdd() { reset(); await loadTypeOptions(); open.value = true; title.value = '新增提示词配置' }
async function handleUpdate(row?: NewsPromptConfig) { reset(); await loadTypeOptions(); const id = row?.id || ids.value[0]; getPrompt(id).then(r => { form.value = r.data; open.value = true; title.value = '修改提示词配置' }) }
async function loadTypeOptions() { try { const r = await listType({ pageNum:1, pageSize:100, isActive:1 }); typeOptions.value = r.rows || [] } catch(e){} }
function submitForm() { (proxy?.$refs['formRef'] as any)?.validate((v: boolean) => { if (v) { const fn = form.value.id ? updatePrompt : addPrompt; fn(form.value).then(() => { proxy?.$modal.msgSuccess('操作成功'); open.value = false; getList() }) } }) }
function handleDelete(row?: NewsPromptConfig) { proxy?.$modal.confirm('确认删除？').then(() => delPrompt(row?.id ? [row.id] : ids.value)).then(() => { getList(); proxy?.$modal.msgSuccess('删除成功') }).catch(() => {}) }
getList()
</script>
