<script setup lang="ts">
import { ref, watch } from "vue";
import { MdEditor } from "md-editor-v3";
import "md-editor-v3/lib/style.css";

const props = defineProps<{
  modelValue?: string;
  placeholder?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const editorRef = ref();

const handleInput = (value: string) => {
  emit("update:modelValue", value);
};

watch(
  () => props.modelValue,
  (val) => {
    if (editorRef.value && val !== (editorRef.value as any).getText()) {
      (editorRef.value as any).setText(val ?? "");
    }
  }
);
</script>

<template>
  <MdEditor
    ref="editorRef"
    :model-value="modelValue"
    :disabled="disabled"
    :placeholder="placeholder || '请输入内容，支持 Markdown 语法和 HTML 标签'"
    :show-code-row="false"
    :no-fullscreen="true"
    :toolbars-exclude="['htmlPreview', 'catalog']"
    model="tab"
    @on-change="handleInput"
    style="border-radius: 4px; border: 1px solid #e4e7ed; overflow: hidden;"
  />
</template>