<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header class="site-header">
    <RouterLink class="brand" to="/">ShopFlow</RouterLink>
  </header>

  <main class="page-shell">
    <RouterView />
  </main>
</template>
