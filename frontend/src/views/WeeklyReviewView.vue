<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { formatDate, statusText, zh } from '../localize'
const router=useRouter(),reviews=ref([]),selected=ref(null),latestDigest=ref(null),memory=ref(null),loading=ref(true),generating=ref(false),error=ref('')
watch(selected,value=>{if(value?.memoryStatistics)memory.value=value.memoryStatistics})
async function load(){loading.value=true;try{[reviews.value,latestDigest.value,memory.value]=await Promise.all([api.weeklyReviews(),api.latestDigest(),api.memoryStatistics()]);selected.value=reviews.value[0]||null;for(const review of reviews.value){const action=review.details?.actions?.find(item=>item.label==='打开每日资料');if(action&&latestDigest.value)action.url=`/workbench?path=${encodeURIComponent(latestDigest.value.markdownPath)}`}}catch(e){error.value=e.message}finally{loading.value=false}}
async function generate(){generating.value=true;error.value='';try{await api.generateWeeklyReview();await load()}catch(e){error.value=e.message}finally{generating.value=false}}
function practice(point){router.push({path:'/quiz',query:{knowledgePoint:point}})}
function actionTarget(action){return action.label==='打开每日资料'&&latestDigest.value?{path:'/workbench',query:{path:latestDigest.value.markdownPath}}:action.url}
onMounted(load)
</script>
<template>
  <section>
    <div class="section-heading"><div><span class="eyebrow">主动学习</span><h2>学习周报</h2><p>集中展示真实学习进展、记忆复习与薄弱点分析。</p></div><el-button type="primary" :loading="generating" @click="generate">生成本周周报</el-button></div>
    <el-alert v-if="error" type="error" :title="error" show-icon @close="error=''"/>
    <el-skeleton v-if="loading" :rows="7" animated/>
    <div v-else-if="!selected" class="empty-card"><el-empty description="还没有学习周报。"><el-button type="primary" :loading="generating" @click="generate">生成第一份周报</el-button></el-empty></div>
    <div v-else class="review-layout">
      <aside class="review-history"><button v-for="review in reviews" :key="review.id" :class="{active:selected.id===review.id}" @click="selected=review"><strong>{{formatDate(review.generatedAt)}}</strong><small>{{statusText[review.status]||review.status}}</small></button></aside>
      <main class="review-card">
      <div class="review-header"><div><span class="eyebrow">自然周</span><h3>{{formatDate(selected.periodStart)}} — {{formatDate(selected.periodEnd)}}</h3></div><el-tag :type="selected.status==='COMPLETE'?'success':'warning'">{{statusText[selected.status]||selected.status}}</el-tag></div>
      <div class="review-stats"><div><strong>{{selected.statistics.tasksCompleted}}</strong><span>已完成任务</span></div><div><strong>{{selected.statistics.questionsAnswered}}</strong><span>已作答题目</span></div><div><strong>{{Math.round(selected.statistics.overallAccuracy*100)}}%</strong><span>正确率</span></div><div><strong>{{selected.details?.overdueTasks||0}}</strong><span>逾期任务</span></div></div>
      <section class="review-section"><h4>薄弱点分析</h4><div v-if="selected.statistics.weakPoints?.length" class="weak-analysis"><article v-for="point in [...selected.statistics.weakPoints].sort((a,b)=>a.accuracy-b.accuracy)" :key="point.knowledgePoint"><div><strong>{{zh(point.knowledgePoint)}}</strong><span>已尝试 {{point.attemptCount}} 次 · 正确率 {{Math.round(point.accuracy*100)}}%</span></div><el-progress :percentage="Math.round(point.accuracy*100)" :stroke-width="9" :color="point.accuracy<.4?'#df5b68':point.accuracy<.6?'#e2a23c':'#45ad7e'"/></article></div><p v-else class="muted">目前没有达到薄弱点判定阈值的知识点。继续完成测验后，分析会自动更新。</p></section>
      <section v-if="memory" class="review-section"><h4>记忆巩固</h4><div class="memory-weekly"><div><strong>{{memory.reviewsThisWeek}}</strong><span>本周复习次数</span></div><div><strong>{{Math.round(memory.reviewSecondsThisWeek/60)}} 分钟</strong><span>总复习时长</span></div><div><strong>{{Math.round(memory.retentionRate*100)}}%</strong><span>真实留存率</span></div><div><strong>{{memory.dueCards}}</strong><span>到期积压</span></div></div><p class="muted">评分分布：忘记 {{memory.ratingDistribution.AGAIN||0}} · 困难 {{memory.ratingDistribution.HARD||0}} · 记得 {{memory.ratingDistribution.GOOD||0}} · 轻松 {{memory.ratingDistribution.EASY||0}}</p><router-link to="/memory">查看知识地图与高风险知识点 →</router-link></section>
      <section class="review-section"><h4>本周总结与反思</h4><p>{{selected.summary}}</p><p class="muted">{{selected.details?.reflection}}</p></section>
      <section class="review-section"><h4>本周成果</h4><ul><li v-for="item in selected.details?.accomplishments||[]" :key="item">{{item}}</li></ul></section>
      <section class="review-section"><h4>分课程进展</h4><div class="course-progress"><article v-for="course in selected.details?.courseProgress||[]" :key="course.courseId"><strong>{{zh(course.courseName)}}</strong><span>作答 {{course.questionsAnswered}} 题 · 正确率 {{Math.round(course.accuracy*100)}}% · 待办 {{course.pendingTasks}}</span></article></div></section>
      <section class="review-section"><h4>下周计划</h4><ol><li v-for="item in selected.details?.nextWeekPlan||[]" :key="item">{{item}}</li></ol></section>
      <section class="review-section"><h4>推荐练习</h4><div v-if="selected.recommendedKnowledgePoints.length" class="practice-list"><button v-for="point in selected.recommendedKnowledgePoints" :key="point" @click="practice(point)"><span>{{zh(point)}}</span><small>选择课程并开始测试 →</small></button></div><p v-else class="muted">没有达到阈值的薄弱知识点。</p></section>
      <section class="review-actions"><router-link v-for="action in selected.details?.actions||[]" :key="action.url" :to="action.url"><el-button>{{action.label}}</el-button></router-link></section>
      </main>
    </div>
  </section>
</template>
