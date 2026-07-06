<script setup lang="ts">
import { ref } from "vue";
import { useEditor, EditorContent } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import { Underline } from "@tiptap/extension-underline";
import { Color } from "@tiptap/extension-color";
import { TextStyle } from "@tiptap/extension-text-style";
import { Placeholder } from "@tiptap/extension-placeholder";
import { Link } from "@tiptap/extension-link";
import { Image } from "@tiptap/extension-image";

const props = defineProps<{
  modelValue?: string;
  placeholder?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const editor = useEditor({
  content: props.modelValue,
  editable: !props.disabled,
  extensions: [
    StarterKit,
    Underline,
    Color,
    TextStyle,
    Link.configure({
      HTMLAttributes: {
        class: "ql-link",
      },
      linkOnPaste: true,
    }),
    Image.configure({
      inline: true,
      HTMLAttributes: {
        class: "ql-image",
      },
    }),
    Placeholder.configure({
      placeholder: props.placeholder || "请输入内容，支持富文本编辑",
    }),
  ],
  onUpdate: () => {
    const content = editor.value?.getHTML() || "";
    emit("update:modelValue", content);
  },
});

const handleTextColor = (e: Event) => {
  const value = (e.target as HTMLInputElement).value;
  editor.value?.chain().focus().setColor(value).run();
};

const showLinkDialog = ref(false);
const linkUrl = ref("");

const openLinkDialog = () => {
  const currentLink = editor.value?.getAttributes("link").href || "";
  linkUrl.value = currentLink;
  showLinkDialog.value = true;
};

const confirmLink = () => {
  if (linkUrl.value) {
    editor.value
      ?.chain()
      .focus()
      .extendMarkRange("link")
      .setLink({ href: linkUrl.value })
      .run();
  } else {
    editor.value?.chain().focus().unsetLink().run();
  }
  showLinkDialog.value = false;
};

const showImageDialog = ref(false);
const imageUrl = ref("");

const openImageDialog = () => {
  imageUrl.value = "";
  showImageDialog.value = true;
};

const confirmImage = () => {
  if (imageUrl.value) {
    editor.value?.chain().focus().setImage({ src: imageUrl.value }).run();
  }
  showImageDialog.value = false;
};
</script>

<template>
  <div class="rich-text-editor">
    <div class="editor-toolbar" v-if="editor">
      <div class="toolbar-group">
        <button
          type="button"
          class="toolbar-btn"
          title="撤销"
          @click="editor.chain().focus().undo().run()"
        >
          ↩
        </button>
        <button
          type="button"
          class="toolbar-btn"
          title="重做"
          @click="editor.chain().focus().redo().run()"
        >
          ↪
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('bold') }"
          title="加粗"
          @click="editor.chain().focus().toggleBold().run()"
        >
          B
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('italic') }"
          title="斜体"
          @click="editor.chain().focus().toggleItalic().run()"
        >
          <i>I</i>
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('underline') }"
          title="下划线"
          @click="editor.chain().focus().toggleUnderline().run()"
        >
          U
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('strike') }"
          title="删除线"
          @click="editor.chain().focus().toggleStrike().run()"
        >
          S
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('code') }"
          title="行内代码"
          @click="editor.chain().focus().toggleCode().run()"
        >
          &lt;&gt;
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('heading', { level: 1 }) }"
          title="标题 1"
          @click="editor.chain().focus().toggleHeading({ level: 1 }).run()"
        >
          H1
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('heading', { level: 2 }) }"
          title="标题 2"
          @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
        >
          H2
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('heading', { level: 3 }) }"
          title="标题 3"
          @click="editor.chain().focus().toggleHeading({ level: 3 }).run()"
        >
          H3
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('paragraph') }"
          title="正文"
          @click="editor.chain().focus().setParagraph().run()"
        >
          P
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('bulletList') }"
          title="无序列表"
          @click="editor.chain().focus().toggleBulletList().run()"
        >
          •
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('orderedList') }"
          title="有序列表"
          @click="editor.chain().focus().toggleOrderedList().run()"
        >
          1.
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('blockquote') }"
          title="引用"
          @click="editor.chain().focus().toggleBlockquote().run()"
        >
          ""
        </button>
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('codeBlock') }"
          title="代码块"
          @click="editor.chain().focus().toggleCodeBlock().run()"
        >
          &lt;/&gt;
        </button>
        <button
          type="button"
          class="toolbar-btn"
          title="分割线"
          @click="editor.chain().focus().setHorizontalRule().run()"
        >
          —
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <button
          type="button"
          class="toolbar-btn"
          :class="{ active: editor.isActive('link') }"
          title="插入链接"
          @click="openLinkDialog"
        >
          🔗
        </button>
        <button
          type="button"
          class="toolbar-btn"
          title="插入图片"
          @click="openImageDialog"
        >
          🖼
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <label class="color-picker-label" title="文字颜色">
          <span class="color-label-text">A</span>
          <span class="color-preview">
            <span class="color-preview-inner" :style="{ backgroundColor: editor.getAttributes('textStyle').color || '#303133' }"></span>
          </span>
          <input
            type="color"
            class="color-picker-input"
            value="#303133"
            @input="handleTextColor"
          />
        </label>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <button
          type="button"
          class="toolbar-btn"
          title="清除格式"
          @click="editor.chain().focus().unsetAllMarks().clearNodes().run()"
        >
          ✕
        </button>
      </div>
    </div>

    <div class="editor-content">
      <EditorContent :editor="editor" />
    </div>

    <!-- Link dialog -->
    <el-dialog
      v-model="showLinkDialog"
      title="插入链接"
      width="420px"
    >
      <el-input
        v-model="linkUrl"
        placeholder="请输入链接地址，例如 https://example.com"
      />
      <template #footer>
        <el-button @click="showLinkDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmLink">确定</el-button>
      </template>
    </el-dialog>

    <!-- Image dialog -->
    <el-dialog
      v-model="showImageDialog"
      title="插入图片"
      width="420px"
    >
      <el-input
        v-model="imageUrl"
        placeholder="请输入图片地址，例如 https://example.com/image.png"
      />
      <template #footer>
        <el-button @click="showImageDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmImage">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rich-text-editor {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  box-sizing: border-box;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: wrap;
}

.toolbar-group {
  display: flex;
  gap: 2px;
  align-items: center;
}

.toolbar-divider {
  width: 1px;
  height: 16px;
  background: #dcdfe6;
  margin: 0 4px;
}

.toolbar-btn {
  min-width: 24px;
  height: 24px;
  padding: 0 4px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: #606266;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
  position: relative;
}

.toolbar-btn:hover {
  background: #ebeef5;
  color: #303133;
}

.toolbar-btn.active {
  background: #ecf5ff;
  color: #409eff;
}

.toolbar-btn i {
  font-style: italic;
  font-weight: 600;
}

/* Color picker */
.color-picker-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 4px;
  border: none;
  border-radius: 3px;
  background: transparent;
  cursor: pointer;
  position: relative;
}

.color-picker-label:hover {
  background: #ebeef5;
}

.color-label-text {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  line-height: 1;
}

.color-preview {
  width: 16px;
  height: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.color-preview-inner {
  width: 12px;
  height: 12px;
  border-radius: 1px;
}

.color-picker-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.editor-content {
  width: 100%;
  box-sizing: border-box;
}

.editor-content :deep(.ProseMirror) {
  width: 100%;
  min-height: 180px;
  padding: 12px 15px;
  font-size: 14px;
  line-height: 1.6;
  box-sizing: border-box;
  outline: none;
  border: none;
}

.editor-content :deep(.ProseMirror h1) {
  font-size: 20px;
  margin: 8px 0 4px 0;
}

.editor-content :deep(.ProseMirror h2) {
  font-size: 18px;
  margin: 6px 0 4px 0;
}

.editor-content :deep(.ProseMirror h3) {
  font-size: 16px;
  margin: 4px 0 4px 0;
}

.editor-content :deep(.ProseMirror p) {
  margin: 4px 0;
}

.editor-content :deep(.ProseMirror ul),
.editor-content :deep(.ProseMirror ol) {
  padding-left: 20px;
  margin: 4px 0;
}

.editor-content :deep(.ProseMirror blockquote) {
  border-left: 3px solid #dcdfe6;
  padding-left: 12px;
  margin: 8px 0;
  color: #909399;
}

.editor-content :deep(.ProseMirror pre) {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
  margin: 8px 0;
  font-family: monospace;
}

.editor-content :deep(.ProseMirror code) {
  background: #f5f7fa;
  border-radius: 3px;
  padding: 1px 4px;
  font-size: 13px;
  color: #e83e8c;
}

.editor-content :deep(.ProseMirror hr) {
  border: none;
  border-top: 1px solid #e4e7ed;
  margin: 12px 0;
}

.editor-content :deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
}

.editor-content :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  color: #c0c4cc;
  font-style: italic;
  pointer-events: none;
  position: absolute;
}
</style>