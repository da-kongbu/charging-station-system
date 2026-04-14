import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * AI 智能推荐状态管理
 *
 * 将 AI 推荐结果从 Home 组件生命周期中剥离，
 * 使其跟随"任务"而非"页面"存在。
 */
export const useSmartSearchStore = defineStore('smartSearch', () => {
  const stations = ref([])
  const summary = ref('')
  const active = ref(false)

  const hasResults = computed(() => active.value && stations.value.length > 0)

  function setResults(data, text) {
    stations.value = data
    summary.value = text || ''
    active.value = true
  }

  function clear() {
    stations.value = []
    summary.value = ''
    active.value = false
  }

  return { stations, summary, active, hasResults, setResults, clear }
})
