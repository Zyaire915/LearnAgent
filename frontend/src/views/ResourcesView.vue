<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { formatDateTime } from '../localize'
import { renderMarkdown } from '../markdown'

const collections=ref([]),selected=ref(null),content=ref(''),search=ref(''),loading=ref(true),loadingDocument=ref(false),error=ref('')
const totalResources=computed(()=>collections.value.reduce((sum,item)=>sum+(item.resources?.length||0),0))
const visibleResources=computed(()=>{const keyword=search.value.trim().toLowerCase();const values=selected.value?.resources||[];return keyword?values.filter(item=>`${item.title} ${item.summary} ${item.sourceName}`.toLowerCase().includes(keyword)):values})
const preview=computed(()=>renderMarkdown(content.value))

onMounted(async()=>{try{collections.value=await api.externalResourceCollections();if(collections.value.length)await choose(collections.value[0])}catch(e){error.value=e.message}finally{loading.value=false}})
async function choose(item){selected.value=item;content.value='';loadingDocument.value=true;error.value='';try{const file=await api.sandboxFile(item.markdownPath);content.value=file.content}catch(e){error.value=e.message}finally{loadingDocument.value=false}}
function kindLabel(kind){return kind==='PROJECT_RESEARCH'?'项目资料':'每日资料'}
</script>

<template>
  <section class="resources-page">
    <div class="section-heading"><div><span class="eyebrow">可追溯知识来源</span><h2>外部资料</h2><p>集中查看项目调研与每日推荐，保留原始链接并清理网页抓取噪声。</p></div><div class="resource-metrics"><span><strong>{{collections.length}}</strong> 份资料集</span><span><strong>{{totalResources}}</strong> 个来源</span></div></div>
    <el-alert v-if="error" class="page-alert" type="warning" :title="error" show-icon @close="error=''"/>
    <el-skeleton v-if="loading" :rows="8" animated/>
    <div v-else-if="!collections.length" class="empty-card"><el-empty description="还没有外部资料。创建学习项目或生成每日资料后会显示在这里。"/></div>
    <div v-else class="resources-shell">
      <aside class="resource-collections"><h3>资料集</h3><button v-for="item in collections" :key="item.id" :class="{active:selected?.id===item.id}" @click="choose(item)"><el-tag size="small" effect="plain">{{kindLabel(item.kind)}}</el-tag><strong>{{item.title}}</strong><small>{{item.resources.length}} 个来源 · {{formatDateTime(item.updatedAt)}}</small></button></aside>
      <main class="resource-content">
        <div class="resource-toolbar"><div><span class="eyebrow">{{kindLabel(selected.kind)}}</span><h3>{{selected.title}}</h3></div><div><router-link :to="{path:'/workbench',query:{path:selected.markdownPath}}"><el-button>在工作台编辑</el-button></router-link></div></div>
        <el-input v-model="search" clearable placeholder="在当前资料集中搜索标题、来源或摘要"/>
        <section class="resource-links"><article v-for="item in visibleResources" :key="item.id"><div class="resource-link-head"><a :href="item.url" target="_blank" rel="noopener noreferrer">{{item.title||'未命名资料'}} ↗</a><el-tag size="small">{{item.sourceName||'Web'}}</el-tag></div><p>{{item.summary||'该来源暂无摘要，请打开原始链接查看。'}}</p><small>收集于 {{formatDateTime(item.discoveredAt)}}</small></article><el-empty v-if="!visibleResources.length" :image-size="56" description="当前筛选条件下没有资料"/></section>
        <section class="resource-markdown"><div class="panel-title"><h3>整理后的 Markdown</h3><span>{{selected.markdownPath}}</span></div><el-skeleton v-if="loadingDocument" :rows="6" animated/><article v-else class="markdown-preview" v-html="preview"/></section>
      </main>
    </div>
  </section>
</template>
