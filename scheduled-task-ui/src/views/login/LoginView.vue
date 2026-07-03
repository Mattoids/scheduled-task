<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import type { LoginRequest } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const form = ref<LoginRequest>({
  username: 'admin',
  password: 'admin123',
})

const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.value)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-background">
      <div class="login-shape shape-1"></div>
      <div class="login-shape shape-2"></div>
      <div class="login-shape shape-3"></div>
    </div>

    <div class="login-container">
      <div class="login-brand">
        <div class="login-logo">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M12 2L2 7L12 12L22 7L12 2Z"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              d="M2 17L12 22L22 17"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              d="M2 12L12 17L22 12"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </div>
        <h1 class="login-brand-title">定时任务报表系统</h1>
        <p class="login-brand-desc">自动化报表生成与分发平台</p>
      </div>

      <el-card class="login-card" shadow="never">
        <h2 class="login-title">欢迎回来</h2>
        <p class="login-subtitle">请登录您的账号</p>

        <el-form :model="form" label-position="top" size="large" @keyup.enter="handleLogin">
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="Lock"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-button"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4f46e5 100%);
  position: relative;
  overflow: hidden;
  padding: 40px 20px;
}

.login-background {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.login-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
}

.shape-1 {
  width: 500px;
  height: 500px;
  background: #6366f1;
  top: -150px;
  right: -100px;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: #8b5cf6;
  bottom: -100px;
  left: -100px;
}

.shape-3 {
  width: 300px;
  height: 300px;
  background: #ec4899;
  top: 40%;
  left: 30%;
  opacity: 0.2;
}

.login-container {
  display: flex;
  align-items: center;
  gap: 80px;
  max-width: 1000px;
  width: 100%;
  z-index: 1;
}

.login-brand {
  flex: 1;
  color: #fff;
  text-align: left;
}

.login-logo {
  width: 64px;
  height: 64px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.login-logo svg {
  width: 36px;
  height: 36px;
  color: #fff;
}

.login-brand-title {
  font-size: 42px;
  font-weight: 700;
  margin-bottom: 16px;
  text-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.login-brand-desc {
  font-size: 18px;
  opacity: 0.85;
  font-weight: 300;
}

.login-card {
  width: 420px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.35);
  padding: 12px;
}

.login-title {
  text-align: center;
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 8px;
}

.login-subtitle {
  text-align: center;
  color: #6b7280;
  margin-bottom: 32px;
  font-size: 15px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
  height: 46px;
  font-size: 16px;
  font-weight: 500;
}

@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
    gap: 32px;
  }

  .login-brand {
    text-align: center;
  }

  .login-logo {
    margin: 0 auto 24px;
  }

  .login-brand-title {
    font-size: 28px;
  }

  .login-card {
    width: 100%;
    max-width: 400px;
  }
}
</style>
