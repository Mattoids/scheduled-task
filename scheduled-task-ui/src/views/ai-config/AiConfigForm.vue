<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { createAiConfig, getAiConfig, updateAiConfig, testAiConfigData } from "@/api/aiConfig";
import type { AiConfig } from "@/types/entity";

interface Props {
  visible: boolean;
  id?: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  "update:visible": [value: boolean];
  success: [];
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit("update:visible", val),
});

const providerOptions = [
  { label: "OpenAI / 兼容", value: "OPENAI" },
  { label: "Anthropic", value: "ANTHROPIC" },
  { label: "Azure OpenAI", value: "AZURE_OPENAI" },
  { label: "Ollama", value: "OLLAMA" },
  { label: "自定义", value: "CUSTOM" },
];

const loading = ref(false);
const formRef = ref();
const form = ref<AiConfig>({
  configName: "",
  provider: "OPENAI",
  apiKey: "",
  baseUrl: "",
  model: "",
  temperature: 0.7,
  maxTokens: 2048,
  timeoutSeconds: 60,
  isDefault: 0,
  status: 1,
  remark: "",
});

const rules = {
  configName: [{ required: true, message: "请输入配置名称", trigger: "blur" }],
  provider: [{ required: true, message: "请选择 AI 厂商", trigger: "change" }],
  model: [{ required: true, message: "请输入模型名称", trigger: "blur" }],
};

const isEdit = computed(() => !!props.id);
const title = computed(() => (isEdit.value ? "编辑 AI 配置" : "新增 AI 配置"));

const resetForm = () => {
  form.value = {
    configName: "",
    provider: "OPENAI",
    apiKey: "",
    baseUrl: "",
    model: "",
    temperature: 0.7,
    maxTokens: 2048,
    timeoutSeconds: 60,
    isDefault: 0,
    status: 1,
    remark: "",
  };
};

const loadDetail = async () => {
  if (!props.id) return;
  loading.value = true;
  try {
    const res = await getAiConfig(props.id);
    form.value = { ...res, apiKey: "" };
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetForm();
      if (props.id) loadDetail();
    }
  },
);

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    const data = { ...form.value };
    if (isEdit.value && !data.apiKey) {
      delete data.apiKey;
    }
    if (isEdit.value) {
      await updateAiConfig(props.id!, data);
    } else {
      await createAiConfig(data);
    }
    ElMessage.success(isEdit.value ? "修改成功" : "新增成功");
    emit("success");
  } finally {
    loading.value = false;
  }
};

const handleTest = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    const data = { ...form.value };
    if (isEdit.value && !data.apiKey) {
      const old = await getAiConfig(props.id!);
      data.apiKey = old.apiKey;
    }
    const res = await testAiConfigData(data);
    ElMessageBox.alert(res || "测试成功，未返回内容", "AI 配置测试成功", {
      type: "success",
    });
  } catch (e: any) {
    ElMessage.error(e?.message || "测试失败");
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="700px">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="dialog-form"
      label-width="100px"
      v-loading="loading"
    >
      <el-form-item label="配置名称" prop="configName">
        <el-input
          v-model="form.configName"
          placeholder="例如：DeepSeek 生产环境"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="AI 厂商" prop="provider">
            <el-select v-model="form.provider" placeholder="请选择 AI 厂商">
              <el-option
                v-for="item in providerOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模型" prop="model">
            <el-input v-model="form.model" placeholder="例如：gpt-4o" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="API Key">
        <el-input
          v-model="form.apiKey"
          type="password"
          placeholder="编辑时留空表示不修改"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item label="Base URL">
        <el-input
          v-model="form.baseUrl"
          placeholder="可选，覆盖默认地址，例如：https://api.deepseek.com"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="Temperature">
            <el-input-number
              v-model="form.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              controls-position="right"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Max Tokens">
            <el-input-number
              v-model="form.maxTokens"
              :min="1"
              :max="128000"
              controls-position="right"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="超时(秒)">
            <el-input-number
              v-model="form.timeoutSeconds"
              :min="1"
              :max="300"
              controls-position="right"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="配置用途说明"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设为默认">
            <el-switch
              v-model="form.isDefault"
              :active-value="1"
              :inactive-value="0"
              active-text="是"
              inactive-text="否"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-switch
              v-model="form.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="禁用"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="success" :loading="loading" @click="handleTest"
        >测试</el-button
      >
      <el-button type="primary" :loading="loading" @click="handleSubmit"
        >确定</el-button
      >
    </template>
  </el-dialog>
</template>
