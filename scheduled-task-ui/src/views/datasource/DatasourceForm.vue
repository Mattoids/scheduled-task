<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  createDatasource,
  getDatasource,
  updateDatasource,
  testDatasourceConfig,
} from "@/api/datasource";
import ConnectionTestProgress from "./ConnectionTestProgress.vue";
import type { DatasourceConfig } from "@/types/entity";
import type { StageResult } from "@/types/index";

type SshAuthType = "password" | "key";

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

const loading = ref(false);
const formRef = ref();
const sshAuthType = ref<SshAuthType>("password");
const testStages = ref<StageResult[]>([]);
const defaultJdbcParams =
  "useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";

const form = ref<DatasourceConfig>({
  name: "",
  dbType: "mysql",
  host: "",
  port: 3306,
  databaseName: "",
  username: "",
  password: "",
  driverClass: "",
  jdbcUrlParams: defaultJdbcParams,
  sshEnabled: 0,
  sshHost: "",
  sshPort: 22,
  sshUsername: "",
  sshPassword: "",
  sshPrivateKey: "",
  sshPassphrase: "",
  remark: "",
  status: 1,
});

const rules = {
  name: [{ required: true, message: "请输入名称", trigger: "blur" }],
  dbType: [{ required: true, message: "请选择类型", trigger: "change" }],
  host: [{ required: true, message: "请输入主机", trigger: "blur" }],
  port: [{ required: true, message: "请输入端口", trigger: "blur" }],
  databaseName: [
    { required: true, message: "请输入数据库名", trigger: "blur" },
  ],
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  sshHost: [{ required: true, message: "请输入 SSH 主机", trigger: "blur" }],
  sshUsername: [
    { required: true, message: "请输入 SSH 用户名", trigger: "blur" },
  ],
  sshPassword: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (
          form.value.sshEnabled === 1 &&
          sshAuthType.value === "password" &&
          !isEdit.value &&
          !value
        ) {
          callback(new Error("请输入 SSH 密码"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
  sshPrivateKey: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (
          form.value.sshEnabled === 1 &&
          sshAuthType.value === "key" &&
          !isEdit.value &&
          !value
        ) {
          callback(new Error("请输入 SSH 私钥"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
};

const isEdit = computed(() => !!props.id);
const title = computed(() => (isEdit.value ? "编辑数据源" : "新增数据源"));

const resetForm = () => {
  sshAuthType.value = "password";
  testStages.value = [];
  form.value = {
    name: "",
    dbType: "mysql",
    host: "",
    port: 3306,
    databaseName: "",
    username: "",
    password: "",
    driverClass: "",
    jdbcUrlParams: defaultJdbcParams,
    sshEnabled: 0,
    sshHost: "",
    sshPort: 22,
    sshUsername: "",
    sshPassword: "",
    sshPrivateKey: "",
    sshPassphrase: "",
    remark: "",
    status: 1,
  };
};

const loadDetail = async () => {
  if (!props.id) return;
  loading.value = true;
  try {
    const res = await getDatasource(props.id);
    form.value = {
      ...res,
    };
    sshAuthType.value = res.sshAuthType || "password";
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

watch(sshAuthType, (type) => {
  if (type === "password") {
    form.value.sshPrivateKey = "";
    form.value.sshPassphrase = "";
  } else {
    form.value.sshPassword = "";
  }
});

const buildSubmitData = () => {
  const data = { ...form.value, sshAuthType: sshAuthType.value };
  if (isEdit.value && !data.password) {
    delete data.password;
  }
  if (sshAuthType.value === "password") {
    delete data.sshPrivateKey;
    delete data.sshPassphrase;
  } else {
    delete data.sshPassword;
  }
  return data;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    const data = buildSubmitData();
    if (isEdit.value) {
      await updateDatasource(props.id!, data);
    } else {
      await createDatasource(data);
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
  testStages.value = [];
  try {
    const data = buildSubmitData();
    const res = await testDatasourceConfig(data);
    testStages.value = res.stages || [];
    if (res.success) {
      ElMessage.success("连接成功");
    } else {
      const stageText =
        res.stage === "SSH"
          ? "SSH 连接失败"
          : res.stage === "DATABASE"
            ? "数据库连接失败"
            : "连接失败";
      ElMessage.error(`${stageText}：${res.message || "未知错误"}`);
    }
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="800px">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="dialog-form"
      label-width="100px"
      v-loading="loading"
    >
      <el-form-item label="数据源名称" prop="name">
        <el-input v-model="form.name" placeholder="数据源名称" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="数据库类型" prop="dbType">
            <el-select v-model="form.dbType" placeholder="请选择">
              <el-option label="MySQL" value="mysql" />
              <el-option label="PostgreSQL" value="postgresql" />
              <el-option label="Oracle" value="oracle" />
              <el-option label="SQL Server" value="sqlserver" />
            </el-select>
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

      <el-row :gutter="16">
        <el-col :span="16">
          <el-form-item label="主机" prop="host">
            <el-input v-model="form.host" placeholder="主机地址" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="端口" prop="port">
            <el-input-number
              v-model="form.port"
              :min="1"
              :max="65535"
              controls-position="right"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="数据库名" prop="databaseName">
            <el-input v-model="form.databaseName" placeholder="数据库名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="用户名" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="密码">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="留空表示不修改"
          show-password
        />
      </el-form-item>

      <el-form-item label="JDBC 参数">
        <el-input
          v-model="form.jdbcUrlParams"
          placeholder="如 useUnicode=true"
        />
      </el-form-item>

      <el-form-item label="启用 SSH">
        <el-switch
          v-model="form.sshEnabled"
          :active-value="1"
          :inactive-value="0"
        />
      </el-form-item>

      <template v-if="form.sshEnabled === 1">
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="SSH 主机">
              <el-input v-model="form.sshHost" placeholder="SSH 主机" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SSH 端口">
              <el-input-number
                v-model="form.sshPort"
                :min="1"
                :max="65535"
                controls-position="right"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="SSH 用户名">
          <el-input v-model="form.sshUsername" placeholder="SSH 用户名" />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-radio-group v-model="sshAuthType">
            <el-radio label="password">密码</el-radio>
            <el-radio label="key">私钥</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="sshAuthType === 'password'" label="SSH 密码">
          <el-input
            v-model="form.sshPassword"
            type="password"
            placeholder="留空表示不修改"
            show-password
          />
        </el-form-item>
        <template v-if="sshAuthType === 'key'">
          <el-form-item label="SSH 私钥">
            <el-input
              v-model="form.sshPrivateKey"
              type="textarea"
              :rows="3"
              placeholder="留空表示不修改"
            />
          </el-form-item>
          <el-form-item label="私钥密码">
            <el-input
              v-model="form.sshPassphrase"
              type="password"
              placeholder="留空表示不修改"
              show-password
            />
          </el-form-item>
        </template>
      </template>

      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          placeholder="备注"
        />
      </el-form-item>
    </el-form>

    <ConnectionTestProgress
      :stages="testStages"
      :ssh-enabled="form.sshEnabled === 1"
    />

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="loading" @click="handleTest">测试连接</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit"
        >确定</el-button
      >
    </template>
  </el-dialog>
</template>
