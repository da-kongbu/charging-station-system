<script setup>
import { RouterView } from 'vue-router'
import AiChat from '@/components/AiChat.vue'
</script>

<template>
  <RouterView />
  <!-- AI 智能客服悬浮按钮，全局可见 -->
  <AiChat />
</template>

<style>
/* Global app styles handled in style.css */
</style>
