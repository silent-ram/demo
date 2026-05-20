<template>
  <div class="login-container">
    <!-- 左侧可爱动画区 -->
    <div class="brand-section" @click="onCatClick" @mousemove="onMouseMove">
      <div class="brand-canvas">
        <div class="bg-circle bc1"></div>
        <div class="bg-circle bc2"></div>
        <div class="bg-circle bc3"></div>
        <div ref="lottieContainer" class="lottie-cat"></div>
        <div v-if="hearts.length" class="hearts-layer">
          <div
            v-for="h in hearts" :key="h.id"
            class="float-heart"
            :style="{ left: h.x + 'px', top: h.y + 'px' }"
          >&#10084;</div>
        </div>
        <div class="sparkle" :style="{ left: sparkle.x + 'px', top: sparkle.y + 'px', opacity: sparkle.show }"></div>
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, getUserInfo } from '@/api/user'
import { useUserStore } from '@/stores/user'
import lottie from 'lottie-web'
import catAnim from '@/assets/lottie/cute-cat.json'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const lottieContainer = ref(null)
let anim = null
let heartId = 0

const hearts = ref([])
const sparkle = reactive({ x: 0, y: 0, show: 0 })

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

function onMouseMove(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  sparkle.x = e.clientX - rect.left
  sparkle.y = e.clientY - rect.top
  sparkle.show = 1
  clearTimeout(sparkle._timer)
  sparkle._timer = setTimeout(() => { sparkle.show = 0 }, 300)
}

function onCatClick(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const id = ++heartId
  hearts.value.push({ id, x, y })
  setTimeout(() => {
    hearts.value = hearts.value.filter(h => h.id !== id)
  }, 1200)
  if (anim) {
    anim.setSpeed(2.5)
    setTimeout(() => anim.setSpeed(1), 600)
  }
}

onMounted(() => {
  if (lottieContainer.value) {
    anim = lottie.loadAnimation({
      container: lottieContainer.value,
      renderer: 'svg',
      loop: true,
      autoplay: true,
      animationData: catAnim
    })
  }
})

onUnmounted(() => {
  anim?.destroy()
})

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
/* ==================== 容器布局 ==================== */
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #faf8f5 0%, #f5f2ed 50%, #ebe7e0 100%);
  position: relative;
  overflow: hidden;
}

/* ==================== 左侧品牌动画区 ==================== */
.brand-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background: transparent;
}

.brand-canvas {
  position: relative;
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 背景装饰圆 */
.bg-circle {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  animation: pulse 4s ease-in-out infinite;
}

.bc1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(200, 170, 140, 0.1) 0%, transparent 70%);
  top: -80px;
  left: -100px;
  animation-delay: 0s;
}

.bc2 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(190, 160, 130, 0.08) 0%, transparent 70%);
  bottom: -50px;
  right: -60px;
  animation-delay: 1.5s;
}

.bc3 {
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(210, 180, 150, 0.08) 0%, transparent 70%);
  top: 30%;
  right: 10%;
  animation-delay: 3s;
}

/* ==================== 漂浮爱心 ==================== */
.lottie-cat {
  width: 360px;
  height: 360px;
  position: relative;
  z-index: 5;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.lottie-cat:hover {
  transform: scale(1.05);
}

.lottie-cat:active {
  transform: scale(0.95);
}

.hearts-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 10;
}

.float-heart {
  position: absolute;
  color: #e85d6f;
  font-size: 20px;
  animation: heart-rise 1.2s ease-out forwards;
  pointer-events: none;
}

.sparkle {
  position: absolute;
  width: 12px;
  height: 12px;
  background: radial-gradient(circle, rgba(255, 200, 100, 0.8), transparent);
  border-radius: 50%;
  pointer-events: none;
  transition: opacity 0.3s ease;
  z-index: 8;
  filter: blur(2px);
}

@keyframes heart-rise {
  0% { transform: translateY(0) scale(1); opacity: 1; }
  100% { transform: translateY(-80px) scale(0.3); opacity: 0; }
}

/* ==================== 动画关键帧 ==================== */
@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.08); opacity: 1; }
}

/* ==================== 登录表单区 ==================== */
.login-section {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 480px;
  min-width: 380px;
  padding-right: 8%;
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

/* ==================== 响应式 ==================== */
@media (max-width: 1100px) {
  .brand-section {
    display: none;
  }
  .login-section {
    width: 100%;
    padding-right: 0;
    justify-content: center;
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
