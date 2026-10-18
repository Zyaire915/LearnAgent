<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Reading, ChatDotRound, DocumentChecked, List, Connection, Calendar, FolderOpened, Timer, Collection } from '@element-plus/icons-vue'
import NotificationBell from './components/NotificationBell.vue'
const route = useRoute()
const title = computed(() => route.meta.title || 'LearnAgent')
const nav = [
  ['/dashboard', '学习概览', DataAnalysis], ['/courses', '课程资料', Reading], ['/chat', 'AI 对话', ChatDotRound],
  ['/quiz', '练习测验', DocumentChecked], ['/tasks', '学习任务', List], ['/skills', 'Agent 技能', Connection], ['/weekly-review', '学习周报', Calendar],
  ['/workbench', '项目工作台', FolderOpened],
  ['/memory', '记忆巩固', Timer],
  ['/resources', '外部资料', Collection],
]
</script>
<template>
  <el-container class="shell">
    <el-aside width="244px" class="sidebar">
      <router-link class="brand" to="/dashboard"><span class="brand-mark">L</span><span>LearnAgent</span></router-link>
      <p class="workspace-label">智能学习空间</p>
      <el-menu :default-active="route.path" router class="nav-menu">
        <el-menu-item v-for="[path, label, icon] in nav" :key="path" :index="path"><el-icon><component :is="icon"/></el-icon><span>{{ label }}</span></el-menu-item>
      </el-menu>
      <div class="sidebar-footer"><span class="status-dot"/> 演示环境就绪</div>
    </el-aside>
    <el-container><el-header class="topbar"><div><h1>{{ title }}</h1><p>你的智能学习工作台</p></div><div class="topbar-actions"><NotificationBell/><el-avatar class="user-avatar" :size="40">祎含</el-avatar></div></el-header>
      <el-main class="content"><router-view/></el-main>
    </el-container>
  </el-container>
</template>
