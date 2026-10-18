<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { formatDateTime } from '../localize'
const router=useRouter(),count=ref(0),items=ref([]),visible=ref(false);let timer
async function refresh(){try{const [counter,notifications]=await Promise.all([api.unreadNotificationCount(),api.notifications(true)]);count.value=counter.count||0;items.value=[...notifications].sort((a,b)=>(b.type==='MEMORY_REVIEW')-(a.type==='MEMORY_REVIEW')||new Date(b.createdAt)-new Date(a.createdAt)).slice(0,8)}catch{}}
async function open(item){await api.readNotification(item.id);visible.value=false;await refresh();if(item.actionUrl)router.push(item.actionUrl)}
async function readAll(){await api.readAllNotifications();await refresh()}
onMounted(()=>{refresh();timer=setInterval(refresh,60000)});onBeforeUnmount(()=>clearInterval(timer))
</script>
<template><el-popover v-model:visible="visible" placement="bottom-end" :width="390" trigger="click"><template #reference><el-badge :value="count" :hidden="!count"><el-button circle :icon="Bell"/></el-badge></template><div class="notification-head"><strong>学习收件箱</strong><el-button text @click="readAll">全部已读</el-button></div><div v-if="items.length" class="notification-list"><button v-for="item in items" :key="item.id" @click="open(item)"><strong>{{item.title}}</strong><span>{{item.body}}</span><small>{{formatDateTime(item.createdAt)}}</small></button></div><el-empty v-else :image-size="56" description="没有未读通知"/></el-popover></template>
