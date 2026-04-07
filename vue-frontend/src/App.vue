<script setup>
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AiChat from '@/components/AiChat.vue'
import Header from '@/components/common/Header.vue'

const route = useRoute()
const isAdminPage = computed(() => route.path.startsWith('/admin'))
</script>

<template>
  <v-app>
    <Header v-if="!isAdminPage" />
    <v-main :style="isAdminPage ? 'padding: 0 !important;' : ''">
      <router-view />
      <AiChat v-if="!isAdminPage" />
    </v-main>
  </v-app>
</template>
