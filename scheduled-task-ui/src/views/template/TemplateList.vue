<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { usePagination } from "@/composables/usePagination";
import { pageTemplate, deleteTemplate, uploadTemplate } from "@/api/template";
import type { ReportTemplate } from "@/types/entity";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
appStore.setBreadcrumb([{ title: "报表模板" }]);

const { current, size, total, records, buildQuery, setPageResult, reset } =
  usePagination();
const loading = ref(false);
const queryForm = reactive({ templateName: "" });
const uploadVisible = ref(false);
const uploadForm = reactive({
  templateName: "",
  templateCode: "",
  description: "",
  file: null as File | null,
});

const loadPage = async () => {
  loading.value = true;
  try {
    const res = await pageTemplate(buildQuery(queryForm));
    setPageResult(res);
  } finally {
    loading.value = false;
  }
};
const handleSearch = () => {
  current.value = 1;
  loadPage();
};
const handleReset = () => {
  queryForm.templateName = "";
  reset();
  loadPage();
};
const handleDelete = async (row: ReportTemplate) => {
  await ElMessageBox.confirm("确认删除该模板？", "提示", { type: "warning" });
  await deleteTemplate(row.id!);
  ElMessage.success("删除成功");
  loadPage();
};

const handleFileChange = (file: any) => {
  uploadForm.file = file.raw;
};

const handleUpload = async () => {
  if (
    !uploadForm.file ||
    !uploadForm.templateName ||
    !uploadForm.templateCode
  ) {
    ElMessage.warning("请填写完整信息并选择文件");
    return;
  }
  const formData = new FormData();
  formData.append("file", uploadForm.file);
  formData.append("templateName", uploadForm.templateName);
  formData.append("templateCode", uploadForm.templateCode);
  if (uploadForm.description) {
    formData.append("description", uploadForm.description);
  }
  await uploadTemplate(formData);
  ElMessage.success("上传成功");
  uploadVisible.value = false;
  uploadForm.templateName = "";
  uploadForm.templateCode = "";
  uploadForm.description = "";
  uploadForm.file = null;
  loadPage();
};

const handlePageChange = (c: number, s: number) => {
  current.value = c;
  size.value = s;
  loadPage();
};

onMounted(loadPage);
</script>

<template>
  <div class="page-card">
    <BaseSearchForm @search="handleSearch" @reset="handleReset">
      <el-row>
        <el-col :span="6">
          <el-form-item label="模板名称">
            <el-input
              v-model="queryForm.templateName"
              placeholder="模板名称"
              clearable
            />
          </el-form-item>
        </el-col>
      </el-row>
    </BaseSearchForm>

    <div class="table-toolbar">
      <el-button
        type="primary"
        v-permission="'template:create'"
        @click="uploadVisible = true"
        >上传模板</el-button
      >
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="templateName" label="模板名称" min-width="160" />
      <el-table-column prop="templateCode" label="模板编码" min-width="140" />
      <el-table-column prop="templateType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.templateType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="fileName"
        label="文件名"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="description"
        label="描述"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column prop="createTime" label="上传时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            v-permission="'template:delete'"
            @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <BasePagination
      :total="total"
      :current="current"
      :size="size"
      @change="handlePageChange"
    />

    <el-dialog v-model="uploadVisible" title="上传模板" width="520px">
      <el-form class="dialog-form" label-width="100px">
        <el-form-item label="模板名称">
          <el-input v-model="uploadForm.templateName" placeholder="模板名称" />
        </el-form-item>
        <el-form-item label="模板编码">
          <el-input v-model="uploadForm.templateCode" placeholder="模板编码" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="2"
            placeholder="描述"
          />
        </el-form-item>
        <el-form-item label="模板文件">
          <el-upload
            accept=".xls,.xlsx,.doc,.docx,.ppt,.pptx,.csv,.txt"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">
                支持 Excel / Word / PPT / CSV / TXT
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>
