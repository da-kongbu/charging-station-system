<template>
  <!-- FAB -->
  <v-btn
    v-if="!isOpen"
    icon
    color="primary"
    size="large"
    position="fixed"
    location="bottom end"
    class="ma-6"
    elevation="6"
    @click="toggleChat"
  >
    <v-badge v-if="messages.length > 0" dot color="error" floating>
      <v-icon>mdi-robot</v-icon>
    </v-badge>
    <v-icon v-else>mdi-robot</v-icon>
    <v-tooltip activator="parent" location="left">智充助手</v-tooltip>
  </v-btn>

  <v-btn
    v-else
    icon
    color="grey-darken-2"
    size="large"
    position="fixed"
    location="bottom end"
    class="ma-6"
    elevation="4"
    @click="toggleChat"
  >
    <v-icon>mdi-close</v-icon>
  </v-btn>

  <!-- Chat Window -->
  <Transition name="chat-slide">
    <v-card
      v-if="isOpen"
      rounded="lg"
      elevation="12"
      width="400"
      class="chat-window"
    >
      <!-- Header -->
      <div class="chat-header pa-4 d-flex align-center justify-space-between">
        <div class="d-flex align-center ga-3">
          <v-avatar color="rgba(255,255,255,0.25)" size="36">
            <v-icon color="white">mdi-robot</v-icon>
          </v-avatar>
          <div>
            <div class="text-subtitle-2 font-weight-bold text-white">智充 AI 助手</div>
            <div class="text-caption" style="color:rgba(255,255,255,0.8)">基于 DeepSeek 大模型 · RAG 知识增强</div>
          </div>
        </div>
        <v-btn icon="mdi-close" variant="text" size="small" color="white" @click="toggleChat" />
      </div>

      <!-- Messages -->
      <div class="chat-messages" ref="messagesContainer">
        <div class="message assistant">
          <v-avatar size="30" color="grey-lighten-3" class="flex-shrink-0">
            <v-icon size="18" color="primary">mdi-robot</v-icon>
          </v-avatar>
          <div class="message-bubble assistant-bubble">
            您好！我是<strong>智充助手</strong>，专为充电桩用户服务的 AI 客服。<br>
            您可以问我：<br>
            • 充电桩怎么使用？<br>
            • 计费规则是什么？<br>
            • 充电枪拔不出来怎么办？
          </div>
        </div>

        <div v-for="(msg, index) in messages" :key="index" class="message" :class="msg.role">
          <v-avatar v-if="msg.role === 'user'" size="30" color="primary" class="flex-shrink-0">
            <v-icon size="18" color="white">mdi-account</v-icon>
          </v-avatar>
          <v-avatar v-else size="30" color="grey-lighten-3" class="flex-shrink-0">
            <v-icon size="18" color="primary">mdi-robot</v-icon>
          </v-avatar>
          <div class="message-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'assistant-bubble'" v-html="formatMessage(msg.content)" />
        </div>

        <div v-if="isLoading" class="message assistant">
          <v-avatar size="30" color="grey-lighten-3" class="flex-shrink-0">
            <v-icon size="18" color="primary">mdi-robot</v-icon>
          </v-avatar>
          <div class="message-bubble assistant-bubble typing">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>

      <!-- Input -->
      <div class="pa-3 border-t">
        <v-text-field
          v-model="inputText"
          variant="outlined"
          density="compact"
          rounded="pill"
          placeholder="输入您的问题..."
          hide-details
          :disabled="isLoading"
          @keydown.enter="sendMessage"
          append-inner-icon="mdi-send"
          @click:append-inner="sendMessage"
        />
      </div>
    </v-card>
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

const API_BASE = 'http://localhost:8080/api/ai'
const useAgentMode = ref(true)

const { userLocation, getUserLocation } = useLocation()

onMounted(async () => { await getUserLocation() })

function toggleChat() {
  isOpen.value = !isOpen.value
  if (isOpen.value) nextTick(() => {})
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  })
}

function formatMessage(text) {
  if (!text) return ''
  const lines = text.split('\n')
  let html = ''
  let inRootList = false
  let inOrderedList = false
  let orderedItemOpen = false
  let inNestedList = false

  const closeRootList = () => { if (inRootList) { html += '</ul>'; inRootList = false } }
  const closeNestedList = () => { if (inNestedList) { html += '</ul>'; inNestedList = false } }
  const closeOrderedItem = () => { closeNestedList(); if (orderedItemOpen) { html += '</li>'; orderedItemOpen = false } }
  const closeOrderedList = () => { closeOrderedItem(); if (inOrderedList) { html += '</ol>'; inOrderedList = false } }
  const closeAllLists = () => { closeRootList(); closeOrderedList() }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()
    const orderedMatch = trimmed.match(/^(\d+)\.\s+(.+)/)
    const unorderedMatch = trimmed.match(/^[\-\*]\s+(.+)/)

    if (/^### (.+)/.test(trimmed)) { closeAllLists(); html += `<strong style="font-size:1em;display:block;margin:8px 0 4px">${trimmed.replace(/^### /, '')}</strong>`; continue }
    if (/^## (.+)/.test(trimmed)) { closeAllLists(); html += `<strong style="font-size:1.05em;display:block;margin:10px 0 4px">${trimmed.replace(/^## /, '')}</strong>`; continue }

    if (trimmed === '') { if (inRootList || inOrderedList || orderedItemOpen || inNestedList) continue; html += '<div style="height:8px"></div>'; continue }

    if (orderedMatch) { closeRootList(); if (!inOrderedList) { html += '<ol style="margin:4px 0;padding-left:22px">'; inOrderedList = true } else closeOrderedItem(); html += `<li>${inlineMd(orderedMatch[2])}`; orderedItemOpen = true; continue }
    if (unorderedMatch) { const content = unorderedMatch[1]; if (orderedItemOpen) { if (!inNestedList) { html += '<ul style="margin:6px 0 2px;padding-left:18px">'; inNestedList = true } html += `<li>${inlineMd(content)}</li>` } else { closeOrderedList(); if (!inRootList) { html += '<ul style="margin:4px 0;padding-left:18px">'; inRootList = true } html += `<li>${inlineMd(content)}</li>` } continue }

    if (orderedItemOpen) { closeNestedList(); html += `<div style="margin:4px 0">${inlineMd(trimmed)}</div>`; continue }
    closeAllLists(); html += `<div>${inlineMd(trimmed)}</div>`
  }
  closeAllLists()
  return html
}

function inlineMd(text) {
  return text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/\*(.+?)\*/g, '<em>$1</em>')
}

async function sendMessage() {
  const question = inputText.value.trim()
  if (!question || isLoading.value) return

  messages.value.push({ role: 'user', content: question })
  inputText.value = ''
  isLoading.value = true
  scrollToBottom()

  try {
    if (useAgentMode.value) {
      const encodedQuestion = encodeURIComponent(question)
      let url = `${API_BASE}/agent?question=${encodedQuestion}`
      if (userLocation.value) url += `&lat=${userLocation.value.latitude}&lng=${userLocation.value.longitude}`
      const response = await fetch(url)
      const json = await response.json()
      isLoading.value = false
      const answer = (typeof json.data === 'string' && json.data.trim()) ? json.data : (json.code === 200 ? '抱歉，智充助手暂时没有生成有效回复，请稍后再试。' : (json.message || '暂无回复'))
      messages.value.push({ role: 'assistant', content: answer })
    } else {
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
        if (isLoading.value) isLoading.value = false
        messages.value[aiMessageIndex].content += line.slice(5)
        scrollToBottom()
      }
    }
  }
  if (buffer.startsWith('data:')) messages.value[aiMessageIndex].content += buffer.slice(5)
  isLoading.value = false
}

watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped>
.chat-window {
  position: fixed;
  bottom: 96px;
  right: 24px;
  z-index: 9999;
  height: 560px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  background: linear-gradient(135deg, #00b894, #00cec9);
  flex-shrink: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f6fa;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.chat-messages::-webkit-scrollbar { width: 4px; }
.chat-messages::-webkit-scrollbar-thumb { background: #ccc; border-radius: 4px; }

.message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  animation: fadeInUp 0.3s ease;
}
.message.user { flex-direction: row-reverse; }

.message-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 0.875rem;
  line-height: 1.6;
  word-break: break-word;
}

.assistant-bubble {
  background: white;
  color: #2d3436;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.user-bubble {
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
  border-bottom-right-radius: 4px;
}

.typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 12px 18px !important;
}

.dot {
  width: 8px;
  height: 8px;
  background: #00b894;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.chat-slide-enter-active, .chat-slide-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.chat-slide-enter-from, .chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 480px) {
  .v-card { width: 100% !important; height: 100% !important; position: fixed !important; bottom: 0 !important; right: 0 !important; margin: 0 !important; border-radius: 0 !important; }
}
</style>
