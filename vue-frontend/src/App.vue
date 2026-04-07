<script setup>
import { RouterView } from 'vue-router'
import AiChat from '@/components/AiChat.vue'
import Header from '@/components/common/Header.vue'
</script>

<template>
  <v-app>
    <Header />
    <v-main>
      <router-view />
      <AiChat />
    </v-main>
  </v-app>
</template>
