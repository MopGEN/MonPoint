<script setup>
import logo from '@/assets/logo.png'
defineProps({ completa: { type: Boolean, default: false } })
</script>
<template>
  <img v-if="completa" :src="logo" class="logo-completo" alt="MonPoint" width="1254" height="1254" />
  <span v-else class="marca-monpoint">
    <img :src="logo" class="logo-compacto" alt="" width="1254" height="1254" />
    <span>MonPoint <span class="version">v3</span></span>
  </span>
</template>
<style scoped>
.marca-monpoint { display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 700; white-space: nowrap; }
.logo-compacto { width: 40px; height: 40px; object-fit: contain; border-radius: var(--mp-radio); }
.logo-completo { display: block; width: 124px; height: 124px; object-fit: contain; border-radius: var(--mp-radio-tarjeta); }
.version { color: var(--mp-verde); }
</style>
