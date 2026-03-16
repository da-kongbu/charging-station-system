<template>
  <!-- 悬浮 AI 助手按钮 -->
  <div class="ai-chat-fab" @click="toggleChat" :class="{ active: isOpen }">
    <span v-if="!isOpen" class="fab-icon">🤖</span>
    <span v-else class="fab-icon">✕</span>
    <span v-if="!isOpen" class="fab-label">智充助手</span>
  </div>

  <!-- 聊天窗口 -->
  <Transition name="chat-slide">
    <div v-if="isOpen" class="ai-chat-window">
      <!-- 头部 -->
      <div class="chat-header">
        <div class="chat-header-info">
          <div class="chat-avatar">🤖</div>
          <div>
            <div class="chat-title">智充 AI 助手</div>
            <div class="chat-subtitle">基于 DeepSeek 大模型 · RAG 知识增强</div>
          </div>
        </div>
        <button class="chat-close" @click="toggleChat">✕</button>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesContainer">
        <!-- 欢迎消息 -->
        <div class="message assistant">
          <div class="message-avatar">🤖</div>
          <div class="message-bubble">
            您好！我是<strong>智充助手</strong>，专为充电桩用户服务的 AI 客服。<br>
            您可以问我：<br>
            • 充电桩怎么使用？<br>
            • 计费规则是什么？<br>
            • 充电枪拔不出来怎么办？
          </div>
        </div>

        <!-- 历史消息 -->
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message"
          :class="msg.role"
        >
          <div class="message-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="message-bubble" v-html="formatMessage(msg.content)"></div>
        </div>

        <!-- AI 正在输入指示器 -->
        <div v-if="isLoading" class="message assistant">
          <div class="message-avatar">🤖</div>
          <div class="message-bubble typing">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <input
          v-model="inputText"
          @keydown.enter="sendMessage"
          placeholder="输入您的问题..."
          :disabled="isLoading"
          class="chat-input"
          ref="chatInput"
        />
        <button
          @click="sendMessage"
          :disabled="isLoading || !inputText.trim()"
          class="chat-send-btn"
        >
          {{ isLoading ? '⏳' : '➤' }}
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { useLocation } from '@/composables/useLocation'

const isOpen = ref(false)
const isLoading = ref(false)
const inputText = ref('')
const messages = ref([])
const messagesContainer = ref(null)
const chatInput = ref(null)

const API_BASE = 'http://localhost:8080/api/ai'

// Agent 模式开关：true = 使用 Agent（能查数据库），false = 使用 SSE 流式
const useAgentMode = ref(true)

// 获取用户位置（复用已有的 useLocation composable）
const { userLocation, getUserLocation } = useLocation()

onMounted(async () => {
  // 页面加载时就获取位置，避免用户发消息时再等
  await getUserLocation()
})

function toggleChat() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    nextTick(() => chatInput.value?.focus())
  }
}

/** 滚动到底部 */
function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

/** 轻量 Markdown → HTML 渲染 */
function formatMessage(text) {
  if (!text) return ''

  // 按行拆分处理
  const lines = text.split('\n')
  let html = ''
  let inList = false   // 是否正在无序列表中
  let inOList = false  // 是否正在有序列表中

  for (let i = 0; i < lines.length; i++) {
    let line = lines[i]

    // 1. 标题 ### → <h4>，## → <h3>，# → <h2>
    if (/^### (.+)/.test(line)) {
      if (inList) { html += '</ul>'; inList = false }
      if (inOList) { html += '</ol>'; inOList = false }
      html += `<strong style="font-size:1em;display:block;margin:8px 0 4px">${line.replace(/^### /, '')}</strong>`
      continue
    }
    if (/^## (.+)/.test(line)) {
      if (inList) { html += '</ul>'; inList = false }
      if (inOList) { html += '</ol>'; inOList = false }
      html += `<strong style="font-size:1.05em;display:block;margin:10px 0 4px">${line.replace(/^## /, '')}</strong>`
      continue
    }

    // 2. 无序列表 - xxx 或 * xxx
    if (/^[\-\*]\s+(.+)/.test(line)) {
      if (inOList) { html += '</ol>'; inOList = false }
      if (!inList) { html += '<ul style="margin:4px 0;padding-left:18px">'; inList = true }
      const content = line.replace(/^[\-\*]\s+/, '')
      html += `<li>${inlineMd(content)}</li>`
      continue
    }

    // 3. 有序列表 1. xxx
    if (/^\d+\.\s+(.+)/.test(line)) {
      if (inList) { html += '</ul>'; inList = false }
      if (!inOList) { html += '<ol style="margin:4px 0;padding-left:18px">'; inOList = true }
      const content = line.replace(/^\d+\.\s+/, '')
      html += `<li>${inlineMd(content)}</li>`
      continue
    }

    // 非列表行：关闭之前打开的列表
    if (inList) { html += '</ul>'; inList = false }
    if (inOList) { html += '</ol>'; inOList = false }

    // 4. 空行 → 段落间距
    if (line.trim() === '') {
      html += '<div style="height:8px"></div>'
      continue
    }

    // 5. 普通文本行
    html += `<div>${inlineMd(line)}</div>`
  }

  // 收尾：关闭未关闭的列表
  if (inList) html += '</ul>'
  if (inOList) html += '</ol>'

  return html
}

/** 行内 Markdown：**加粗**、*斜体* */
function inlineMd(text) {
  return text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
}

/** 发送消息 */
async function sendMessage() {
  const question = inputText.value.trim()
  if (!question || isLoading.value) return

  // 1. 添加用户消息
  messages.value.push({ role: 'user', content: question })
  inputText.value = ''
  isLoading.value = true
  scrollToBottom()

  try {
    if (useAgentMode.value) {
      // ===== Agent 模式：带位置信息请求 =====
      const encodedQuestion = encodeURIComponent(question)
      let url = `${API_BASE}/agent?question=${encodedQuestion}`

      // 把用户 GPS 位置传给后端，后端会按距离排序
      if (userLocation.value) {
        url += `&lat=${userLocation.value.latitude}&lng=${userLocation.value.longitude}`
      }

      const response = await fetch(url)
      const json = await response.json()
      isLoading.value = false
      const answer = json.data || json.message || '暂无回复'
      messages.value.push({ role: 'assistant', content: answer })
    } else {
      // ===== 流式模式：提前创建空气泡用于逐字追加 =====
      const aiMessageIndex = messages.value.length
      messages.value.push({ role: 'assistant', content: '' })
      await streamChat(question, aiMessageIndex)
    }
  } catch (error) {
    console.error('AI 对话失败:', error)
    isLoading.value = false
    messages.value.push({ role: 'assistant', content: '抱歉，智充助手暂时无法响应，请稍后再试。' })
  } finally {
    scrollToBottom()
  }
}

/** SSE 流式聊天 */
async function streamChat(question, aiMessageIndex) {
  const encodedQuestion = encodeURIComponent(question)
  const response = await fetch(`${API_BASE}/stream?question=${encodedQuestion}`)

  if (!response.ok) throw new Error(`请求失败: ${response.status}`)

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop()

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5)
        if (isLoading.value) isLoading.value = false
        messages.value[aiMessageIndex].content += data
        scrollToBottom()
      }
    }
  }

  if (buffer.startsWith('data:')) {
    messages.value[aiMessageIndex].content += buffer.slice(5)
  }
  isLoading.value = false
}

// 监听消息变化自动滚动
watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped>
/* ========== 悬浮按钮 ========== */
.ai-chat-fab {
  position: fixed;
  bottom: 30px;
  right: 30px;
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
  border-radius: 50px;
  padding: 14px 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 6px 20px rgba(0, 184, 148, 0.4);
  transition: all 0.3s ease;
  z-index: 9998;
  user-select: none;
}

.ai-chat-fab:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 30px rgba(0, 184, 148, 0.5);
}

.ai-chat-fab.active {
  border-radius: 50%;
  padding: 14px;
  background: linear-gradient(135deg, #636e72, #2d3436);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.fab-icon {
  font-size: 1.3rem;
  line-height: 1;
}

.fab-label {
  font-size: 0.9rem;
  font-weight: 600;
  letter-spacing: 0.5px;
}

/* ========== 聊天窗口 ========== */
.ai-chat-window {
  position: fixed;
  bottom: 100px;
  right: 30px;
  width: 400px;
  height: 560px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 9999;
}

/* ========== 头部 ========== */
.chat-header {
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-avatar {
  font-size: 1.6rem;
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-title {
  font-weight: 700;
  font-size: 1rem;
}

.chat-subtitle {
  font-size: 0.72rem;
  opacity: 0.85;
  margin-top: 2px;
}

.chat-close {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  font-size: 1.1rem;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.chat-close:hover {
  background: rgba(255, 255, 255, 0.4);
}

/* ========== 消息区域 ========== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f6fa;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-messages::-webkit-scrollbar {
  width: 4px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 4px;
}

/* ========== 消息气泡 ========== */
.message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  animation: fadeInUp 0.3s ease;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  font-size: 1.3rem;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.message-bubble {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 0.9rem;
  line-height: 1.6;
  word-break: break-word;
}

.message.assistant .message-bubble {
  background: white;
  color: #2d3436;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.message.user .message-bubble {
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
  border-bottom-right-radius: 4px;
}

/* ========== 打字指示器 ========== */
.typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 14px 20px !important;
}

.dot {
  width: 8px;
  height: 8px;
  background: #00b894;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* ========== 输入区域 ========== */
.chat-input-area {
  padding: 14px 16px;
  background: white;
  border-top: 1px solid #eee;
  display: flex;
  gap: 10px;
}

.chat-input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 25px;
  font-size: 0.9rem;
  outline: none;
  transition: all 0.3s;
}

.chat-input:focus {
  border-color: #00b894;
  box-shadow: 0 0 0 3px rgba(0, 184, 148, 0.1);
}

.chat-input:disabled {
  background: #f5f5f5;
}

.chat-send-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
  font-size: 1.2rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  flex-shrink: 0;
}

.chat-send-btn:hover:not(:disabled) {
  transform: scale(1.08);
  box-shadow: 0 4px 12px rgba(0, 184, 148, 0.4);
}

.chat-send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ========== 动画 ========== */
.chat-slide-enter-active,
.chat-slide-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-slide-enter-from,
.chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 响应式：手机端全屏聊天 ========== */
@media (max-width: 480px) {
  .ai-chat-window {
    width: 100%;
    height: 100%;
    bottom: 0;
    right: 0;
    border-radius: 0;
  }

  .ai-chat-fab {
    bottom: 20px;
    right: 20px;
  }
}
</style>
