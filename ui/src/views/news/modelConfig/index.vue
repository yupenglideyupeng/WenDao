<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="提供商" prop="provider">
        <el-select v-model="queryParams.provider" placeholder="全部" clearable>
          <el-option v-for="p in news_model_provider" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['news:model:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['news:model:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="名称" prop="name" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="提供商" prop="provider" width="100" align="center">
        <template #default="scope">
          <dict-tag :options="news_model_provider" :value="scope.row.provider" />
        </template>
      </el-table-column>
      <el-table-column label="模型" prop="modelName" width="150" :show-overflow-tooltip="true" />
      <el-table-column label="优先级" prop="priority" width="70" align="center" />
      <el-table-column label="适用场景" prop="usageType" width="200" align="center">
        <template #default="scope">
          <el-tag style="margin-left: 5px" v-for="t in parseUsageTypes(scope.row.usageType)" :key="t" size="small" class="ml-1" :type="usageTagType(t)">{{ usageLabel(t) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="API格式" prop="apiFormat" width="110" align="center">
        <template #default="scope">
          <dict-tag :options="news_api_format" :value="scope.row.apiFormat" />
        </template>
      </el-table-column>
      <el-table-column label="JSON" prop="supportJsonMode" width="60" align="center">
        <template #default="scope">{{ scope.row.supportJsonMode === 1 ? '✅' : '❌' }}</template>
      </el-table-column>
      <el-table-column label="流式" prop="supportStream" width="60" align="center">
        <template #default="scope">{{ scope.row.supportStream === 1 ? '✅' : '❌' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="isActive" width="70" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isActive === 1 ? 'success' : 'danger'" size="small">{{ scope.row.isActive === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="240">
        <template #default="scope">
          <el-button link type="success" icon="Connection" @click="handleTest(scope.row)" v-hasPermi="['news:model:query']">测试</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['news:model:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['news:model:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="配置名称" prop="name"><el-input v-model="form.name" placeholder="如：DeepSeek主模型" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="提供商" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择">
                <el-option v-for="p in news_model_provider" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="API地址" prop="apiUrl"><el-input v-model="form.apiUrl" placeholder="https://api.deepseek.com/v1/chat/completions" /></el-form-item>
        <el-form-item label="API密钥" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="form.id ? '留空则不修改密钥' : '请输入API密钥'" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName"><el-input v-model="form.modelName" placeholder="如：deepseek-chat" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority"><el-input-number v-model="form.priority" :min="1" :max="99" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="max_tokens" prop="maxTokens"><el-input-number v-model="form.maxTokens" :min="100" :max="8192" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度" prop="temperature"><el-input-number v-model="form.temperature" :min="0" :max="2" :precision="2" :step="0.1" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="超时(毫秒)" prop="timeoutMs"><el-input-number v-model="form.timeoutMs" :min="1000" :max="300000" :step="1000" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重试次数" prop="retryCount"><el-input-number v-model="form.retryCount" :min="0" :max="5" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="适用场景" prop="usageType">
          <el-checkbox-group v-model="usageTypeList">
            <el-checkbox label="INTERPRET">一键解读</el-checkbox>
            <el-checkbox label="ANALYSIS">自动分析</el-checkbox>
            <el-checkbox label="EXPANSION">查询扩展</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="API格式" prop="apiFormat">
          <el-radio-group v-model="form.apiFormat">
            <el-radio v-for="f in news_api_format" :key="f.value" :value="f.value">{{ f.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="JSON模式"><el-switch v-model="form.supportJsonMode" :active-value="1" :inactive-value="0" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="流式输出"><el-switch v-model="form.supportStream" :active-value="1" :inactive-value="0" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="启用状态"><el-switch v-model="form.isActive" :active-value="1" :inactive-value="0" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" placeholder="备注信息" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确定</el-button>
        <el-button v-if="form.id" type="success" @click="handleTest()" :loading="testLoading">测试连接</el-button>
        <el-button @click="cancel">取消</el-button>
      </template>
    </el-dialog>

    <!-- 测试结果弹窗 -->
    <el-dialog v-model="testOpen" title="连接测试" width="450px" append-to-body>
      <el-result :icon="testResult?.success ? 'success' : 'error'" :title="testResult?.success ? '连接成功' : '连接失败'" :sub-title="testResult?.message" />
      <template #footer><el-button @click="testOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NewsModelConfig">
import { listModelConfig, getModelConfig, addModelConfig, updateModelConfig, delModelConfig, testModelConfig } from '@/api/news/modelConfig'
import type { NewsModelConfig, NewsModelConfigQueryParams } from '@/types/api/news/modelConfig'
import { useDict } from '@/utils/dict'
const { proxy } = getCurrentInstance()

const { news_model_provider, news_api_format } = useDict('news_model_provider', 'news_api_format')

const list = ref<NewsModelConfig[]>([]); const open = ref(false); const loading = ref(true); const showSearch = ref(true)
const ids = ref<number[]>([]); const single = ref(true); const multiple = ref(true); const total = ref(0); const title = ref('')
const testOpen = ref(false); const testLoading = ref(false)
const testResult = ref<{ success: boolean; message: string; modelName?: string } | null>(null)
const usageTypeList = ref<string[]>(['INTERPRET', 'ANALYSIS', 'EXPANSION'])

const data = reactive({
  form: { provider: 'DEEPSEEK', priority: 1, maxTokens: 2000, temperature: 0.3, supportJsonMode: 1, supportStream: 1, timeoutMs: 30000, retryCount: 1, isActive: 1, apiFormat: 'OPENAI' } as NewsModelConfig,
  queryParams: { pageNum: 1, pageSize: 10 } as NewsModelConfigQueryParams,
  rules: {
    name: [{ required: true, message: '配置名称不能为空', trigger: 'blur' }],
    provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
    apiUrl: [{ required: true, message: 'API地址不能为空', trigger: 'blur' }],
    modelName: [{ required: true, message: '模型名称不能为空', trigger: 'blur' }],
    priority: [{ required: true, message: '优先级不能为空', trigger: 'blur' }]
  }
})
const { queryParams, form, rules } = toRefs(data)

function parseUsageTypes(ut: string): string[] { return ut ? ut.split(',') : ['ALL'] }
function usageLabel(t: string) {
  const m: Record<string, string> = { INTERPRET: '解读', ANALYSIS: '分析', EXPANSION: '扩展', ALL: '全部' }
  return m[t] || t
}
function usageTagType(t: string) { const m: Record<string, string> = { INTERPRET: 'warning', ANALYSIS: 'success', EXPANSION: '', ALL: 'primary' }; return m[t] || 'info' }

function getList() { loading.value = true; listModelConfig(queryParams.value).then(r => { list.value = r.rows; total.value = r.total; loading.value = false }) }
function cancel() { open.value = false; reset() }
function reset() {
  form.value = { provider: 'DEEPSEEK', priority: 1, maxTokens: 2000, temperature: 0.3, supportJsonMode: 1, supportStream: 1, usageType: '', timeoutMs: 30000, retryCount: 1, isActive: 1 } as NewsModelConfig
  usageTypeList.value = ['INTERPRET', 'ANALYSIS', 'EXPANSION']
  proxy?.resetForm('formRef')
}
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy?.resetForm('queryRef'); handleQuery() }
function handleSelectionChange(s: NewsModelConfig[]) { ids.value = s.map(i => i.id as number); single.value = s.length !== 1; multiple.value = !s.length }
function handleAdd() { reset(); open.value = true; title.value = '新增模型配置' }
function handleUpdate(row?: NewsModelConfig) {
  reset()
  getModelConfig(row?.id || ids.value[0]).then(r => {
    form.value = r.data
    // apiKey 脱敏了，编辑时清空让用户重新输入
    if (form.value.apiKey && form.value.apiKey.includes('****')) form.value.apiKey = ''
    usageTypeList.value = form.value.usageType ? form.value.usageType.split(',') : ['ALL']
    open.value = true; title.value = '修改模型配置'
  })
}
function submitForm() {
  (proxy?.$refs['formRef'] as any)?.validate((valid: boolean) => {
    if (!valid) return
    form.value.usageType = usageTypeList.value.join(',')
    const fn = form.value.id ? updateModelConfig : addModelConfig
    fn(form.value).then(() => { proxy?.$modal.msgSuccess('操作成功'); open.value = false; getList() })
  })
}
function handleDelete(row?: NewsModelConfig) {
  proxy?.$modal.confirm('确认删除？').then(() => delModelConfig(row?.id ? [row.id] : ids.value)).then(() => { getList(); proxy?.$modal.msgSuccess('删除成功') }).catch(() => {})
}

async function handleTest(row?: NewsModelConfig) {
  const id = row?.id || form.value.id
  if (!id) { proxy?.$modal.msgWarning('请先保存配置后再测试'); return }
  testLoading.value = true
  try {
    const res = await testModelConfig(id)
    testResult.value = res.data
    testOpen.value = true
  } catch (e: any) { testResult.value = { success: false, message: e?.message || '测试请求异常' }; testOpen.value = true }
  finally { testLoading.value = false }
}

getList()
</script>
