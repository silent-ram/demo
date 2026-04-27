<template>
  <div class="login-container">
    <!-- 左侧品牌区 -->
    <div class="brand-section">
      <div class="brand-card">
        <div class="brand-decoration">
          <div class="deco-circle dc1"></div>
          <div class="deco-circle dc2"></div>
          <div class="deco-circle dc3"></div>
        </div>

        <div class="brand-icon">
          <el-icon :size="48"><Monitor /></el-icon>
        </div>
        <h3 class="brand-title">智能预警系统</h3>
        <p class="brand-subtitle">Industrial Fault Warning</p>

        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-icon">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="feature-text">
              <h4>实时监测</h4>
              <p>全天候设备状态监控</p>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="feature-text">
              <h4>精准预测</h4>
              <p>AI 驱动的故障预警</p>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <el-icon><Tools /></el-icon>
            </div>
            <div class="feature-text">
              <h4>高效维护</h4>
              <p>智能化工单派发</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 登录表单区 -->
    <div class="login-section">
      <div class="login-card animate-fade-scale">
        <div class="login-header">
          <div class="logo-icon">
            <el-icon :size="32"><Shield /></el-icon>
          </div>
          <h2 class="login-title">故障预警系统</h2>
          <p class="login-desc">Fault Warning System</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username" class="animate-fade-in delay-100">
            <div class="input-group">
              <label class="input-label">
                <el-icon><User /></el-icon>
                用户名
              </label>
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                size="large"
                class="stylish-input"
              />
            </div>
          </el-form-item>

          <el-form-item prop="password" class="animate-fade-in delay-200">
            <div class="input-group">
              <label class="input-label">
                <el-icon><Lock /></el-icon>
                密码
              </label>
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                class="stylish-input"
                show-password
              />
            </div>
          </el-form-item>

          <el-form-item class="animate-fade-in delay-300">
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-button"
              @click="handleLogin"
            >
              <span v-if="!loading" class="btn-content">
                <el-icon class="btn-icon"><Key /></el-icon>
                登 录
              </span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 底部版权 -->
    <div class="footer-deco animate-fade-in delay-500">
      <span>© 2026 工业设备故障预警系统</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, getUserInfo } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login(form)
    userStore.setToken(res.data.token)

    const infoRes = await getUserInfo()
    userStore.setUserInfo(infoRes.data)

    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #faf8f5 0%, #f5f2ed 50%, #ebe7e0 100%);
  position: relative;
  overflow: hidden;
}

/* 装饰圆形 */
.brand-card {
  position: relative;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.dc1 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, rgba(232, 93, 4, 0.12), rgba(244, 162, 97, 0.08));
  top: -100px;
  left: -150px;
}

.dc2 {
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, rgba(0, 119, 182, 0.1), rgba(0, 168, 232, 0.06));
  bottom: -50px;
  right: -80px;
}

.dc3 {
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, rgba(45, 147, 108, 0.1), rgba(45, 147, 108, 0.05));
  top: 50%;
  left: -60px;
}

/* 左侧品牌区 */
.brand-section {
  display: none;
}

.brand-card {
  background: linear-gradient(135deg, #ffffff, #f5f2ed);
  border-radius: 28px;
  padding: 52px 48px;
  text-align: center;
  position: relative;
  overflow: hidden;
  max-width: 340px;
  box-shadow: 0 25px 80px rgba(45, 42, 38, 0.12);
  border: 1px solid rgba(45, 42, 38, 0.08);
}

.brand-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.brand-icon {
  width: 90px;
  height: 90px;
  background: linear-gradient(135deg, #e85d04, #ff7b3d);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin: 0 auto 24px;
  position: relative;
  z-index: 1;
  box-shadow: 0 10px 40px rgba(232, 93, 4, 0.35);
}

.brand-title {
  font-family: 'Playfair Display', 'Noto Serif SC', Georgia, serif;
  font-size: 28px;
  font-weight: 700;
  color: #2d2a26;
  letter-spacing: 3px;
  margin-bottom: 8px;
  position: relative;
  z-index: 1;
}

.brand-subtitle {
  font-size: 12px;
  color: #9a948c;
  letter-spacing: 3px;
  text-transform: uppercase;
  margin-bottom: 48px;
  position: relative;
  z-index: 1;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(45, 42, 38, 0.04);
  border: 1px solid rgba(45, 42, 38, 0.06);
  border-radius: 14px;
  text-align: left;
  transition: all 0.3s ease;
}

.feature-item:hover {
  background: rgba(45, 42, 38, 0.08);
  transform: translateX(5px);
}

.feature-icon {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, rgba(232, 93, 4, 0.3), rgba(244, 162, 97, 0.2));
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff7b3d;
  font-size: 20px;
}

.feature-text h4 {
  color: #2d2a26;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
}

.feature-text p {
  color: #9a948c;
  font-size: 12px;
}

/* 登录表单区 */
.login-section {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
  padding-right: 10%;
}

.login-card {
  background: white;
  border: 1px solid rgba(45, 42, 38, 0.08);
  border-radius: 28px;
  padding: 40px 44px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(45, 42, 38, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #e85d04, #ff7b3d);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin: 0 auto 16px;
  box-shadow: 0 6px 25px rgba(232, 93, 4, 0.25);
}

.login-title {
  font-family: 'Playfair Display', 'Noto Serif SC', Georgia, serif;
  font-size: 26px;
  font-weight: 700;
  color: #2d2a26;
  letter-spacing: 2px;
  margin-bottom: 4px;
}

.login-desc {
  color: #9a948c;
  font-size: 12px;
  letter-spacing: 2px;
  text-transform: uppercase;
}

/* 表单样式 */
.login-form {
  margin-top: 8px;
}

.input-group {
  width: 100%;
}

.input-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #5c5750;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.input-label .el-icon {
  color: #e85d04;
  font-size: 16px;
}

.stylish-input :deep(.el-input__wrapper) {
  padding: 14px 16px !important;
  border-radius: 10px !important;
  border: 1px solid rgba(45, 42, 38, 0.12) !important;
  background: #faf8f5 !important;
  box-shadow: none !important;
  transition: all 0.25s ease !important;
}

.stylish-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(45, 42, 38, 0.2) !important;
  background: white !important;
}

.stylish-input :deep(.el-input__wrapper:focus-within) {
  border-color: #e85d04 !important;
  background: white !important;
  box-shadow: 0 0 0 3px rgba(232, 93, 4, 0.1) !important;
}

.stylish-input :deep(.el-input__inner) {
  font-size: 15px;
  color: #2d2a26 !important;
}

.stylish-input :deep(.el-input__inner::placeholder) {
  color: #9a948c !important;
}

.stylish-input :deep(.el-icon) {
  color: #9a948c !important;
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: 10px !important;
  margin-top: 10px;
  background: linear-gradient(135deg, #e85d04, #ff7b3d) !important;
  border: none !important;
  box-shadow: 0 6px 20px rgba(232, 93, 4, 0.25) !important;
  transition: all 0.3s ease !important;
  color: white !important;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 35px rgba(232, 93, 4, 0.35) !important;
}

.login-button:active {
  transform: translateY(0);
}

.btn-content {
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon {
  margin-right: 8px;
}

/* 底部凭证 */
.login-footer {
  margin-top: 32px;
}

.divider-line {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}

.divider-line::before,
.divider-line::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(45, 42, 38, 0.12), transparent);
}

.divider-line span {
  color: #9a948c;
  font-size: 12px;
}

.credentials {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cred-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #faf8f5;
  border: 1px solid rgba(45, 42, 38, 0.06);
  border-radius: 10px;
  font-size: 13px;
  color: #5c5750;
  transition: all 0.25s ease;
}

.cred-item:hover {
  background: #f5f2ed;
  transform: translateX(5px);
}

.cred-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 12px;
  color: #9a948c;
}

/* 底部版权 */
.footer-deco {
  position: fixed;
  bottom: 28px;
  right: 40px;
  color: #9a948c;
  font-size: 11px;
  letter-spacing: 1px;
  z-index: 10;
}

/* 响应式 */
@media (max-width: 1100px) {
  .brand-section {
    display: none;
  }
}

@media (max-width: 500px) {
  .login-card {
    padding: 40px 30px;
    border-radius: 24px;
    margin: 20px;
  }

  .login-title {
    font-size: 24px;
  }

  .footer-deco {
    right: 20px;
    bottom: 20px;
  }
}
</style>
