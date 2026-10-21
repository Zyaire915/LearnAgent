<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { zh } from '../localize'

const route = useRoute(), router = useRouter()
const courses = ref([]), courseId = ref(''), knowledgePoint = ref(''), count = ref(5)
const quiz = ref(null), session = ref(null), answers = ref({}), result = ref(null)
const loading = ref(false), error = ref('')
const historyOpen = ref(false), historyLoading = ref(false), historyItems = ref([]), historyCourseNames = ref({})
const currentRound = computed(() => session.value?.rounds?.at(-1) || null)
const adaptive = computed(() => !!session.value)
const allAnswered = computed(() => quiz.value && Object.keys(answers.value).length === quiz.value.questions.length)
const currentCourse = computed(() => courses.value.find(course => course.id === courseId.value))
const topicPlaceholder = computed(() => `可选：输入“${zh(currentCourse.value?.name || '所选课程')}”中想测试的主题`)

onMounted(async () => {
  knowledgePoint.value = String(route.query.knowledgePoint || '')
  try {
    courses.value = await api.courses()
    historyCourseNames.value = Object.fromEntries(courses.value.map(course => [course.id, zh(course.name)]))
    courseId.value = String(route.query.courseId || courses.value[0]?.id || '')
    await loadHistory()
    if (route.query.sessionId) await loadSession(String(route.query.sessionId))
  } catch (e) { error.value = e.message }
})

async function loadHistory() {
  historyLoading.value = true
  try { historyItems.value = await api.quizHistory() }
  catch (e) { error.value = e.message }
  finally { historyLoading.value = false }
}
async function openHistoryRecord(id) {
  loading.value = true; error.value = ''
  try {
    const record = await api.quizHistoryDetail(id)
    quiz.value = record.quiz; session.value = null
    answers.value = Object.fromEntries(record.answers.map(answer => [answer.questionId, answer.userAnswer]))
    result.value = { totalQuestions: record.totalQuestions, correctCount: record.correctCount,
      accuracy: record.accuracy, results: record.results }
    historyOpen.value = false
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function loadSession(id) {
  session.value = await api.learningSession(id)
  courseId.value = session.value.courseId
  quiz.value = session.value.rounds.at(-1)?.quiz || null
  answers.value = {}; result.value = null
}
async function startAdaptive() {
  loading.value = true; error.value = ''
  try {
    session.value = await api.startLearningSession({ courseId: courseId.value, goal: knowledgePoint.value || `${zh(currentCourse.value?.name||'当前课程')}综合备考`, webEnabled: true })
    quiz.value = session.value.rounds.at(-1)?.quiz || null
    answers.value = {}; result.value = null
    await router.replace({ path: '/quiz', query: { sessionId: session.value.id } })
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function generateOnce() {
  loading.value = true; error.value = ''
  try {
    session.value = null
    quiz.value = await api.generateQuiz({ courseId: courseId.value, knowledgePoint: knowledgePoint.value || null, questionCount: count.value })
    answers.value = {}; result.value = null
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function submit() {
  loading.value = true; error.value = ''
  const submitted = Object.entries(answers.value).map(([questionId, answer]) => ({ questionId, answer }))
  try {
    result.value = adaptive.value
      ? await api.submitLearningRound(session.value.id, currentRound.value.id, submitted)
      : await api.submitQuiz(quiz.value.id, submitted)
    if (adaptive.value) session.value = result.value.session
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function next(action) {
  loading.value = true; error.value = ''
  try {
    session.value = await api.nextLearningAction(session.value.id, action)
    quiz.value = session.value.rounds.at(-1).quiz
    answers.value = {}; result.value = null
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function stopSession() {
  try { session.value = await api.stopLearningSession(session.value.id) }
  catch (e) { error.value = e.message }
}
function reset() { quiz.value = null; session.value = null; result.value = null; answers.value = {}; router.replace('/quiz') }
function courseChanged() { quiz.value = null; session.value = null; result.value = null; answers.value = {}; knowledgePoint.value = '' }
function resultFor(id) {
  const values = adaptive.value ? result.value?.submission?.results : result.value?.results
  return values?.find(item => item.questionId === id)
}
</script>

<template>
  <section>
    <div class="section-heading"><div><span class="eyebrow">主动测评</span><h2>自适应备考</h2><p>选择任意课程与主题，系统会结合课程资料和可追溯的网络资料自动出题。</p></div><el-button :loading="historyLoading" @click="historyOpen=!historyOpen; historyOpen&&loadHistory()">{{historyOpen?'返回测验':'测验记录'}}<el-badge v-if="historyItems.length" :value="historyItems.length" class="history-badge"/></el-button></div>
    <el-alert v-if="error" class="page-alert" type="warning" :title="error" show-icon @close="error=''"/>

    <div v-if="historyOpen" class="document-panel quiz-history">
      <div class="card-header"><div><h3>已完成的测验</h3><p class="muted">切换页面或重新打开网站后，已提交的答题记录仍会保留。</p></div><el-button text :loading="historyLoading" @click="loadHistory">刷新</el-button></div>
      <div v-if="historyItems.length" class="quiz-history-list">
        <button v-for="item in historyItems" :key="item.id" class="quiz-history-item" @click="openHistoryRecord(item.id)">
          <div><strong>{{zh(item.title)}}</strong><small>{{historyCourseNames[item.courseId]||'课程'}} · {{item.knowledgePoint?zh(item.knowledgePoint):'综合练习'}}<template v-if="item.learningRoundNumber"> · 自适应第{{item.learningRoundNumber}}轮</template></small><small>{{new Date(item.completedAt).toLocaleString()}}</small></div>
          <span class="quiz-history-score">{{item.correctCount}} / {{item.totalQuestions}}<small>{{Math.round(item.accuracy*100)}}%</small></span>
        </button>
      </div>
      <el-empty v-else description="还没有已完成的测验。提交一次测验后，成绩和逐题答案会保存在这里。"/>
    </div>

    <div v-else-if="!quiz" class="document-panel quiz-form adaptive-start">
      <el-select v-model="courseId" placeholder="选择课程" @change="courseChanged"><el-option v-for="course in courses" :key="course.id" :label="zh(course.name)" :value="course.id"/></el-select>
      <el-input v-model="knowledgePoint" :placeholder="topicPlaceholder"/>
      <div class="quiz-start-actions">
        <el-button type="primary" :loading="loading" :disabled="!courseId" @click="startAdaptive">开始5题自适应测试</el-button>
        <el-button :loading="loading" :disabled="!courseId" @click="generateOnce">仅按课程资料出题</el-button>
      </div>
      <p class="muted">掌握标准：总正确率≥80%，且每个知识点≥60%；最多连续3轮。</p>
    </div>

    <template v-else>
      <div v-if="adaptive" class="session-bar">
        <div><span class="eyebrow">学习会话</span><strong>{{session.goal}}</strong><small>第 {{currentRound.roundNumber}} / {{session.maxRounds}} 轮 · {{zh(currentRound.focus)}}</small></div>
        <el-tag :type="session.status==='MASTERED'?'success':'info'">{{session.status==='MASTERED'?'已达掌握标准':'进行中'}}</el-tag>
      </div>
      <div v-if="result" class="hero result">
        <div><span class="eyebrow">测验结果</span><h2>答对 {{adaptive?result.submission.correctCount:result.correctCount}} / {{adaptive?result.submission.totalQuestions:result.totalQuestions}} 题</h2><p>正确率 {{Math.round((adaptive?result.submission.accuracy:result.accuracy)*100)}}%</p></div>
        <el-button @click="reset">结束并返回</el-button>
      </div>
      <el-alert v-if="adaptive&&result?.mastered" type="success" :closable="false" title="本轮已达到掌握标准。你可以继续提高难度，也可以结束本次学习。"/>
      <el-alert v-if="adaptive&&result?.candidateFlashcards" type="info" :closable="false" show-icon><template #title>已根据错题生成 {{result.candidateFlashcards}} 张候选记忆卡。候选内容需你确认后才会进入 FSRS 复习队列。</template><router-link :to="{path:'/memory',query:{courseId}}">前往确认记忆卡 →</router-link></el-alert>

      <article v-for="(question,index) in quiz.questions" :key="question.id" class="question-card">
        <h3>{{index+1}}. {{question.content}}</h3>
        <el-radio-group v-model="answers[question.id]" :disabled="!!result"><el-radio v-for="option in question.options" :key="option" :value="option">{{option}}</el-radio></el-radio-group>
        <div v-if="resultFor(question.id)" class="explanation">
          <strong :class="resultFor(question.id).correct?'ok':'bad'">{{resultFor(question.id).correct?'正确':'错误'}} · 正确答案：{{resultFor(question.id).correctAnswer}}</strong>
          <p>{{resultFor(question.id).explanation}}</p><small>{{zh(resultFor(question.id).knowledgePoint)}}</small>
        </div>
        <div v-if="question.sources?.length" class="question-sources"><span>依据：</span><template v-for="source in question.sources.slice(0,3)" :key="source.url||`${source.filename}-${source.pageNumber}`"><a v-if="source.url" :href="source.url" target="_blank">{{source.title}}</a><span v-else>{{source.filename}}<template v-if="source.pageNumber"> 第{{source.pageNumber}}页</template></span></template></div>
      </article>

      <p v-if="!result&&!allAnswered" class="muted">请完成全部题目后再提交。</p>
      <el-button v-if="!result" type="primary" :loading="loading" :disabled="!allAnswered||loading" @click="submit">提交本轮答案</el-button>
      <section v-if="adaptive&&result" class="recommendation-panel">
        <h3>接下来怎么练？</h3><p>LearnAgent 根据本轮表现准备了三个方案。</p>
        <div class="recommendation-grid"><button v-for="item in result.recommendations" :key="item.action" :disabled="!item.enabled||loading" @click="next(item.action)"><strong>{{item.title}}</strong><span>{{item.description}}</span></button></div>
        <el-button text type="danger" @click="stopSession">停止本次测试</el-button>
      </section>
    </template>
  </section>
</template>
