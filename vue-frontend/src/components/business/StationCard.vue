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
  <RouterLink 
    :to="`/station/${station.id}`"
    class="station-card card"
  >
    <div class="station-image">
      <span class="station-icon">🔌</span>
    </div>
    <div class="station-info">
      <h3>{{ station.name }}</h3>
      <p class="station-address">📍 {{ station.city }} {{ station.district }} {{ station.address }}</p>
      <div class="station-meta">
        <span v-if="distanceDisplay" class="badge badge-info">📍 {{ distanceDisplay }}</span>
        <span class="badge badge-success">营业中</span>
        <span class="pile-count">{{ station.piles?.length || 0 }} 个充电桩</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.station-card {
  cursor: pointer;
  transition: all 0.3s ease;
  overflow: hidden;
  display: block;
  text-decoration: none;
  color: inherit;
  background: white;
  border-radius: 12px;
  box-shadow: 0 5px 15px rgba(0,0,0,0.05);
  margin-bottom: 20px;
}

.station-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
}

.station-image {
  height: 120px;
  background: linear-gradient(135deg, #dfe6e9 0%, #b2bec3 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: -20px -20px 15px -20px; /* Adjust based on parent padding if moved, but here it's self-contained style from previous card class */
  /* Re-adjusting for component context */
  margin: 0;
  border-radius: 12px 12px 0 0;
}

/* Card padding needs to be applied to content */
.station-info {
  padding: 15px;
}

.station-icon {
  font-size: 3rem;
}

.station-info h3 {
  font-size: 1.1rem;
  margin-bottom: 8px;
  color: var(--text);
}

.station-address {
  font-size: 0.9rem;
  color: var(--text-light);
  margin-bottom: 12px;
}

.station-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pile-count {
  font-size: 0.85rem;
  color: var(--text-light);
}

.badge {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.badge-info {
  background-color: #e3f2fd;
  color: #1976d2;
}

.badge-success {
  background-color: #e8f5e9;
  color: #2ecc71;
}
</style>
