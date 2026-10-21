<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { formatDateTime } from '../localize'
import { renderMarkdown } from '../markdown'
import { formatReviewReason } from '../resourceReviewText'

const hiddenSources=ref([]),saving=ref(false)
const collections=ref([]),selected=ref(null),content=ref(''),search=ref(''),loading=ref(true),loadingDocument=ref(false),error=ref('')
const totalResources=computed(()=>collections.value.reduce((sum,item)=>sum+(item.resources?.length||0),0))
const projectCollections=computed(()=>collections.value.filter(item=>item.kind==='PROJECT_RESEARCH'))
const dailyCollections=computed(()=>collections.value.filter(item=>item.kind==='DAILY_DIGEST'))
const visibleResources=computed(()=>{const keyword=search.value.trim().toLowerCase();const values=selected.value?.resources||[];return keyword?values.filter(item=>`${item.title} ${item.summary} ${item.sourceName}`.toLowerCase().includes(keyword)):values})
const preview=computed(()=>renderMarkdown(content.value.replace(/(^- 推荐依据：)([^\r\n]*)/gm, (_, prefix, reason) => prefix + formatReviewReason(reason))))

onMounted(async()=>{try{await reload()}catch(e){error.value=e.message}finally{loading.value=false}})
async function reload(){
  const id=selected.value?.id
  const [items,hidden]=await Promise.all([api.externalResourceCollections(),api.hiddenResourceSources()])
  collections.value=items;hiddenSources.value=hidden
  const next=items.find(item=>item.id===id)||items[0]
  if(next)await choose(next);else {selected.value=null;content.value=''}
}
async function changeSource(item,restore=false){
  saving.value=true;error.value=''
  try{if(restore)await api.restoreResourceSource(item);else await api.hideResourceSource(item.id);await reload()}
  catch(e){error.value=e.message}finally{saving.value=false}
}
function reviewLabel(status){return {BODY_REVIEWED:'正文AI审核',AUTO_REVIEWED:'搜索摘要审核',CAUTION:'程序提示：谨慎参考',UNREVIEWED:'尚未审核'}[status]||'尚未审核'}
function reviewDetails(item){return formatReviewReason(item.reviewReason||'')}
function reviewSummary(item){
  const reason=reviewDetails(item)
  if(item.reviewStatus==='BODY_REVIEWED')return reason.match(/通过内容判断文章质量：([^；]+)/)?.[1]||'已根据抓取正文审核学习价值，展开查看评分依据。'
  if(!reason)return '暂无审核依据，请自行核实原始来源。'
  const parts=[]
  if(reason.includes('匹配官方来源名单'))parts.push('域名匹配官方来源名单')
  else if(reason.includes('匹配高校来源名单'))parts.push('域名匹配高校来源名单')
  else if(reason.includes('匹配高校域名后缀'))parts.push('域名符合当时的高校后缀规则')
  else if(reason.includes('匹配开放教育平台'))parts.push('来自已收录的开放教育平台')
  else if(reason.includes('匹配知名社区'))parts.push('来自已收录的社区平台')
  else parts.push('来源尚未匹配优先推荐名单')
  if(reason.includes('标题及摘要均未匹配'))parts.push('标题和摘要未匹配学习主题')
  else if(/标题 \+[1-9]/.test(reason))parts.push('标题包含学习主题关键词')
  else if(/摘要补充 \+[1-9]/.test(reason))parts.push('摘要包含学习主题关键词')
  if(item.reviewStatus==='CAUTION')parts.push('程序提示需谨慎参考，请展开查看依据')
  return parts.join('；')+'。'
}
async function choose(item){selected.value=item;content.value='';loadingDocument.value=true;error.value='';try{const file=await api.sandboxFile(item.markdownPath);content.value=file.content}catch(e){error.value=e.message}finally{loadingDocument.value=false}}
</script>

<template>
  <section class="resources-page">
    <div class="section-heading"><div><span class="eyebrow">可追溯知识来源</span><h2>外部资料</h2><p>集中查看项目调研与每日推荐，保留原始链接并清理网页抓取噪声。</p></div><div class="resource-metrics"><span><strong>{{collections.length}}</strong> 份资料集</span><span><strong>{{totalResources}}</strong> 个来源</span></div></div>
    <p class="review-explanation">新推荐先抓取正文，再由AI审核主题匹配、教学内容和明显质量问题；作者与发布日期仅使用原文依据。排序分不是百分制或正确率。历史资料保留原分数，不会自动补做正文审核；旧版可访问性与互动项已停止展示。</p>
    <el-alert v-if="error" class="page-alert" type="warning" :title="error" show-icon @close="error=''"/>
    <div v-if="hiddenSources.length"><span>已隐藏来源（同域名）：</span><el-button v-for="host in hiddenSources" :key="host" :disabled="saving" @click="changeSource(host,true)">恢复 {{host}}</el-button></div>
    <el-skeleton v-if="loading" :rows="8" animated/>
    <div v-else-if="!collections.length" class="empty-card"><el-empty description="还没有外部资料。创建学习项目或生成每日资料后会显示在这里。"/></div>
    <div v-else class="resources-shell">
      <aside class="resource-collections">
        <h3>资料集</h3>
        <template v-if="projectCollections.length">
          <div class="collection-kind">项目资料</div>
          <button v-for="item in projectCollections" :key="item.id" :class="{active:selected?.id===item.id}" @click="choose(item)"><el-tag size="small" effect="plain" type="success">项目资料</el-tag><strong>{{item.title}}</strong><small>{{item.resources.length}} 个来源 · {{formatDateTime(item.updatedAt)}}</small></button>
        </template>
        <template v-if="dailyCollections.length">
          <div class="collection-kind">每日资料</div>
          <button v-for="item in dailyCollections" :key="item.id" :class="{active:selected?.id===item.id}" @click="choose(item)"><el-tag size="small" effect="plain">每日资料</el-tag><strong>{{item.title}}</strong><small>{{item.resources.length}} 个来源 · {{formatDateTime(item.updatedAt)}}</small></button>
        </template>
      </aside>
      <main class="resource-content">
        <div class="resource-toolbar"><div><span class="eyebrow">{{selected.kind==='PROJECT_RESEARCH'?'项目资料':'每日资料'}}</span><h3>{{selected.title}}</h3></div><div><router-link :to="{path:'/workbench',query:{path:selected.markdownPath}}"><el-button>在工作台编辑</el-button></router-link></div></div>
        <el-input v-model="search" clearable placeholder="在当前资料集中搜索标题、来源或摘要"/>
        <section class="resource-links"><article v-for="item in visibleResources" :key="item.id"><div class="resource-link-head"><a :href="item.url" target="_blank" rel="noopener noreferrer">{{item.title||'未命名资料'}} ↗</a><el-tag size="small">{{item.sourceName||'Web'}}</el-tag></div><p>{{item.summary||'该来源暂无摘要，请打开原始链接查看。'}}</p><p><el-tag :type="item.reviewStatus==='CAUTION'?'warning':'info'">{{reviewLabel(item.reviewStatus)}}</el-tag><span v-if="item.reviewScore!=null"> · 推荐排序分 {{item.reviewScore}}</span></p><p>{{reviewSummary(item)}}</p><details v-if="item.reviewReason"><summary>查看评分依据</summary><p>以下为程序自动审核的评分依据。</p><p>{{reviewDetails(item)}}</p></details><small>检索入库时间 {{formatDateTime(item.discoveredAt)}}</small><el-button link type="warning" :disabled="saving" @click="changeSource(item)">隐藏此域名的来源</el-button></article><el-empty v-if="!visibleResources.length" :image-size="56" description="当前筛选条件下没有资料"/></section>
        <p v-if="selected.hasHiddenSources">已隐藏来源不再显示在推荐列表。历史 Markdown 保留原始记录，可在工作台查看。</p><section v-else class="resource-markdown"><div class="panel-title"><h3>整理后的 Markdown</h3><span>{{selected.markdownPath}}</span></div><el-skeleton v-if="loadingDocument" :rows="6" animated/><article v-else class="markdown-preview" v-html="preview"/></section>
      </main>
    </div>
  </section>
</template>
