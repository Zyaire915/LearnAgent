<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import SourceList from '../components/SourceList.vue'
import { skillName, stepSummary, zh } from '../localize'

const route = useRoute()
const courses = ref([]), courseId = ref(''), message = ref(''), messages = ref([])
const conversations = ref([]), conversationId = ref('')
const loading = ref(false), loadingCourses = ref(true), loadingHistory = ref(false), error = ref('')
const currentCourse = computed(() => courses.value.find(course => course.id === courseId.value))
const courseLabel = computed(() => zh(currentCourse.value?.name || '当前课程'))
const suggestions = computed(() => [
  `测试我对“${courseLabel.value}”核心知识的掌握情况。`,
  `帮我制定“${courseLabel.value}”的复习计划。`,
  `围绕“${courseLabel.value}”创建一个学习项目。`,
])
const chatPlaceholder = computed(() => `例如：测试我的“${courseLabel.value}”薄弱点，或描述一个想创建的学习项目。`)

function chooseSuggestion(value) { message.value = value }
function newConversation() {
  conversationId.value = ''
  messages.value = []
  error.value = ''
}
async function loadConversations() {
  if (!courseId.value) { conversations.value = []; return }
  loadingHistory.value = true
  try { conversations.value = await api.agentConversations(courseId.value) }
  catch (e) { error.value = e.message }
  finally { loadingHistory.value = false }
}
async function openConversation(id) {
  loading.value = true
  error.value = ''
  try {
    const record = await api.agentConversation(id)
    conversationId.value = record.id
    messages.value = record.messages.map(item => ({ role: item.role, text: item.text, sources: item.sources, steps: item.steps }))
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
async function loadCourses() {
  try {
    courses.value = await api.courses()
    if (courses.value.length) courseId.value = String(route.query.courseId || courses.value[0].id)
  } catch (e) { error.value = e.message }
  finally { loadingCourses.value = false }
}
async function send() {
  if (!message.value.trim() || !courseId.value || loading.value) return
  const text = message.value.trim()
  messages.value.push({ role: 'user', text })
  message.value = ''
  loading.value = true
  error.value = ''
  try {
    const response = await api.agentChat({ courseId: courseId.value, conversationId: conversationId.value || null, message: text })
    conversationId.value = response.conversationId
    messages.value.push({ role: 'assistant', text: response.answer, sources: response.sources, steps: response.steps })
    await loadConversations()
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
function actionUrl(item) {
  const match = String(item.text || '').match(/\/(?:quiz|workbench)\?[^\s，。)]+/)
  return match?.[0] || ''
}
function actionLabel(url) { return url.startsWith('/quiz') ? '进入自适应测试 →' : '打开项目工作台 →' }
function formatTime(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

watch(courseId, (next, previous) => {
  if (!next) return
  if (previous && next !== previous) newConversation()
  loadConversations()
})
onMounted(loadCourses)
</script>

<template>
  <section class="chat-layout">
    <div class="section-heading compact">
      <div><span class="eyebrow">AI AGENT</span><h2>LearnAgent 对话</h2><p>对话会自动保存，可从左侧历史记录中重新打开。</p></div>
      <el-select v-model="courseId" :loading="loadingCourses" placeholder="选择课程" class="course-select">
        <el-option v-for="course in courses" :key="course.id" :label="zh(course.name)" :value="course.id"/>
      </el-select>
    </div>
    <el-alert v-if="error" class="page-alert" type="warning" :title="error" show-icon @close="error=''"/>
    <div v-if="!courses.length&&!loadingCourses" class="empty-card"><el-empty description="请先创建课程。"><router-link to="/courses"><el-button type="primary">前往课程</el-button></router-link></el-empty></div>
    <div v-else class="chat-body">
      <aside class="chat-history">
        <div class="chat-history-heading"><strong>对话记录</strong><el-button size="small" @click="newConversation">新对话</el-button></div>
        <div v-if="loadingHistory" class="chat-history-empty">正在读取记录…</div>
        <div v-else-if="!conversations.length" class="chat-history-empty">此课程还没有对话记录</div>
        <button v-for="item in conversations" :key="item.id" class="chat-history-item" :class="{ active: item.id === conversationId }" :disabled="loading" @click="openConversation(item.id)">
          <strong>{{ item.title }}</strong><span>{{ item.preview }}</span><small>{{ formatTime(item.updatedAt) }} · {{ item.messageCount }} 条消息</small>
        </button>
      </aside>
      <div class="chat-main">
        <div class="chat-window">
          <div v-if="!messages.length" class="chat-welcome">
            <span>⌁</span><h3>{{currentCourse?`向 LearnAgent 询问${zh(currentCourse.name)}相关问题`:'选择一门课程'}}</h3>
            <p>可启动自适应测试、查询课程与网络资料、创建任务、生成周报和学习项目。</p>
            <div class="chat-suggestions"><button v-for="suggestion in suggestions" :key="suggestion" @click="chooseSuggestion(suggestion)">{{suggestion}}</button></div>
          </div>
          <article v-for="(item,index) in messages" :key="index" class="message" :class="item.role">
            <div class="message-label">{{item.role==='user'?'你':'LearnAgent'}}</div>
            <div v-if="item.steps?.length" class="agent-steps"><div v-for="(step,stepIndex) in item.steps" :key="stepIndex" class="agent-step" :class="step.status.toLowerCase()"><span class="step-mark">{{step.status==='SUCCESS'?'✓':step.status==='FAILED'?'!':'·'}}</span><div><strong>{{skillName(step.skill)}}</strong><small>{{step.tool}} · {{stepSummary(step.summary)}}</small></div></div></div>
            <p>{{item.text}}</p>
            <router-link v-if="actionUrl(item)" :to="actionUrl(item)" class="task-result-link">{{actionLabel(actionUrl(item))}}</router-link>
            <router-link v-if="item.steps?.some(step=>step.tool==='createTask'&&step.status==='SUCCESS')" to="/tasks" class="task-result-link">查看学习任务 →</router-link>
            <SourceList :sources="item.sources||[]"/>
          </article>
          <div v-if="loading" class="message assistant"><div class="message-label">LearnAgent</div><small>正在分析学习数据并选择合适的 Skill…</small><el-skeleton :rows="2" animated/></div>
        </div>
        <form class="chat-composer" @submit.prevent="send">
          <el-input v-model="message" type="textarea" :rows="3" resize="none" :disabled="loading||!courseId" :placeholder="chatPlaceholder"/>
          <el-button native-type="submit" type="primary" :loading="loading" :disabled="!message.trim()||!courseId">发送</el-button>
        </form>
      </div>
    </div>
  </section>
</template>
