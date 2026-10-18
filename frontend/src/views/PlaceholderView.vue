<script setup>import { useRoute } from 'vue-router'; const route = useRoute()</script>
<template><section class="placeholder"><el-empty :description="route.meta.description"><template #image><div class="placeholder-icon">◌</div></template><el-tag type="info">Phase 1 placeholder</el-tag></el-empty></section></template>
