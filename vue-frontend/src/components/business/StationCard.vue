<script setup>
import { computed } from 'vue'

const props = defineProps({
  station: {
    type: Object,
    required: true
  }
})

const distanceDisplay = computed(() => {
  return props.station.distance ? `${props.station.distance} km` : ''
})
</script>

<template>
  <v-card
    :to="`/station/${station.id}`"
    hover
    rounded="lg"
    class="station-card d-flex flex-column"
  >
    <!-- Image placeholder -->
    <div class="station-image d-flex align-center justify-center">
      <v-icon size="56" color="white">mdi-ev-station</v-icon>
    </div>

    <v-card-text class="pb-2 flex-grow-1 d-flex flex-column">
      <div class="text-subtitle-1 font-weight-bold mb-1 text-truncate">{{ station.name }}</div>
      <div class="text-body-2 text-grey-darken-1 mb-3 line-clamp-2">
        <v-icon size="16" class="mr-1">mdi-map-marker</v-icon>
        {{ station.city }} {{ station.district }} {{ station.address }}
      </div>

      <v-spacer />

      <div class="d-flex align-center justify-space-between">
        <div class="d-flex ga-1">
          <v-chip v-if="distanceDisplay" size="small" color="secondary" variant="tonal" prepend-icon="mdi-map-marker-distance">
            {{ distanceDisplay }}
          </v-chip>
          <v-chip size="small" color="success" variant="tonal">
            营业中
          </v-chip>
        </div>
        <span class="text-caption text-grey">{{ station.piles?.length || 0 }} 个充电桩</span>
      </div>
    </v-card-text>
  </v-card>
</template>

<style scoped>
.station-card {
  transition: transform 0.2s ease;
}
.station-image {
  height: 120px;
  background: linear-gradient(135deg, #00b894 0%, #00cec9 100%);
}
</style>
