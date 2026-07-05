<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from "vue";
import { MdEditor, type EditorProps } from "md-editor-v3";
import "md-editor-v3/lib/style.css";

const props = defineProps<{
  modelValue?: string;
  placeholder?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const editorRef = ref<InstanceType<typeof MdEditor>>();
const theme = ref<"dark" | "light">("light");

const editorProps: Partial<EditorProps> = {
  modelValue: props.modelValue,
  placeholder: props.placeholder || "请输入内容，支持 Markdown 语法和 HTML 标签",
  theme,
  showCodeRow: false,
  noFullscreen: true,
  toolbarsExclude: ["htmlPreview", "catalog"],
  preview: {
    markdown: {
      link: {
        rel: "noopener noreferrer",
        target: "_blank",
      },
    },
  },
};

const handleInput = (value: string) => {
  emit("update:modelValue", value);
};

onMounted(() => {
  theme.value = (document.documentElement.getAttribute("data-theme") || "light") as "light" | "dark";
});

onUnmounted(() => {
  editorRef.value?.destroy();
});

watch(
  () => props.modelValue,
  (val) => {
    if (editorRef.value && val !== editorRef.value?.getText()) {
      editorRef.value.setText(val ?? "");
    }
  }
);
</script>

<template>
  <MdEditor
    ref="editorRef"
    :model-value="modelValue"
    :disabled="disabled"
    :placeholder="placeholder"
    :theme="theme"
    :show-code-row="false"
    :no-fullscreen="true"
    :toolbars-exclude="['htmlPreview', 'catalog']"
    @on-change="handleInput"
    style="border-radius: 4px; border: 1px solid #e4e7ed; overflow: hidden;"
  />
</template>