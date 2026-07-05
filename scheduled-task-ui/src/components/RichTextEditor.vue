<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from "vue";
import Quill from "quill";
import "quill/dist/quill.core.css";
import "quill/dist/quill.snow.css";

const props = defineProps<{
  modelValue?: string;
  placeholder?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const containerRef = ref<HTMLDivElement>();
let editor: Quill | null = null;
let isSyncing = false;

const initEditor = () => {
  if (!containerRef.value) return;

  editor = new Quill(containerRef.value, {
    theme: "snow",
    placeholder: props.placeholder || "请输入内容，支持 Markdown 语法和 HTML 标签",
    modules: {
      toolbar: [
        [{ header: [1, 2, 3, false] }],
        ["bold", "italic", "underline", "strike"],
        [{ color: [] }, { background: [] }],
        [{ list: "ordered" }, { list: "bullet" }],
        ["blockquote", "code-block"],
        ["link", "image"],
        ["clean"],
      ],
    },
    readOnly: props.disabled,
  });

  // Clear Quill's inline width/height set during init - these cause width mismatch
  const container = editor.container;
  container.style.removeProperty("width");
  container.style.removeProperty("height");

  const editorEl = container.querySelector(".ql-editor") as HTMLElement | null;
  if (editorEl) {
    editorEl.style.removeProperty("width");
    editorEl.style.removeProperty("height");
  }

  // Set initial content
  const html = props.modelValue || "";
  if (html) {
    editor.root.innerHTML = html;
  }

  editor.on("text-change", () => {
    if (isSyncing) return;
    isSyncing = true;
    const html = editor?.root.innerHTML || "";
    emit("update:modelValue", html === "<p><br></p>" ? "" : html);
    isSyncing = false;
  });
};

const updateModelValue = (value: string | undefined) => {
  if (!editor || isSyncing) return;
  isSyncing = true;
  const html = value || "";
  editor.root.innerHTML = html;
  isSyncing = false;
};

watch(() => props.disabled, (val) => {
  editor?.enable(!val);
});

onMounted(() => {
  initEditor();
});

onBeforeUnmount(() => {
  editor = null;
});

defineExpose({
  updateModelValue,
});
</script>

<template>
  <div ref="containerRef" class="rich-text-editor" />
</template>

<style scoped>
.rich-text-editor {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  box-sizing: border-box;
}

.rich-text-editor :deep(.ql-toolbar.ql-snow) {
  width: 100%;
  display: block;
  border: none;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
  padding: 8px 0;
  box-sizing: border-box;
}

/* Fixed height prevents container collapse since .ql-editor is position:absolute */
.rich-text-editor :deep(.ql-container.ql-snow) {
  width: 100%;
  height: 180px !important;
  border: none;
  border-top: 0;
  font-size: 14px;
  box-sizing: border-box;
}

/* Force editor to fill container, add scroll for overflow content */
.rich-text-editor :deep(.ql-editor) {
  height: 100% !important;
  overflow-y: auto;
  padding: 12px 15px;
  box-sizing: border-box;
}

.rich-text-editor :deep(.ql-editor.ql-blank::before) {
  color: #c0c4cc;
}

.rich-text-editor :deep(.ql-editor h1) {
  font-size: 20px;
  margin: 8px 0 4px 0;
}

.rich-text-editor :deep(.ql-editor h2) {
  font-size: 18px;
  margin: 6px 0 4px 0;
}

.rich-text-editor :deep(.ql-editor h3) {
  font-size: 16px;
  margin: 4px 0 4px 0;
}

.rich-text-editor :deep(.ql-editor p) {
  margin: 4px 0;
}

.rich-text-editor :deep(.ql-editor pre.ql-syntax) {
  background: #f5f7fa;
  color: #303133;
  border-radius: 4px;
}
</style>