<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { formatDateTime, priorityText, statusText, zh } from '../localize'

const tasks = ref([]), courses = ref([]), filter = ref(''), error = ref(''), loading = ref(true)
const saving = ref(false), busyId = ref(''), dialog = ref(false), editingId = ref(null), viewMode = ref('grouped')
const completedOpen = ref(false)
const blank = () => ({ courseId: null, title: '', description: '', priority: 'MEDIUM', status: 'TODO', dueAt: '' })
const draft = ref(blank())

const projectPrefix = /^\[(.+?)\]\s*/
function projectInfo(task) { const m = task.title.match(projectPrefix); return m ? { name: m[1], title: task.title.slice(m[0].length) } : null }
const isProject = t => !!projectInfo(t)
const isCourse = t => !isProject(t) && !!t.courseId
const isGeneric = t => !isProject(t) && !t.courseId
const byCreatedAt = (a, b) => new Date(a.createdAt) - new Date(b.createdAt)
const byDueAt = (left, right) => {
  if (!left.dueAt && !right.dueAt) return 0
  if (!left.dueAt) return 1
  if (!right.dueAt) return -1
  return new Date(left.dueAt) - new Date(right.dueAt)
}
const circled = ['①','②','③','④','⑤','⑥','⑦','⑧','⑨','⑩','⑪','⑫']

/* 项目分组：项目内保持 AI 拆解顺序（byCreatedAt），单个完成不沉底 */
const projectGroups = computed(() => {
  const map = new Map()
  tasks.value.filter(isProject).forEach(task => {
    const info = projectInfo(task)
    if (!map.has(info.name)) map.set(info.name, [])
    map.get(info.name).push(task)
  })
  return Array.from(map.entries()).map(([name, items]) => ({
    name, items: items.sort(byCreatedAt),
    done: items.filter(t => t.status === 'COMPLETED').length, total: items.length
  })).sort((a, b) => new Date(b.items[0].createdAt) - new Date(a.items[0].createdAt))
})
const activeProjectGroups = computed(() => projectGroups.value.filter(g => g.done < g.total))
const completedProjectGroups = computed(() => projectGroups.value.filter(g => g.done === g.total))

/* 课程分组：按完成状态分离，未完成在上、已完成收底 */
function buildCourseGroups(predicate) {
  const map = new Map()
  tasks.value.filter(t => isCourse(t) && predicate(t)).forEach(task => {
    if (!map.has(task.courseId)) map.set(task.courseId, [])
    map.get(task.courseId).push(task)
  })
  return Array.from(map.entries()).map(([courseId, items]) => ({
    courseId, name: courses.value.find(c => c.id === courseId)?.name || '课程任务',
    items: items.sort(byDueAt), pending: items.filter(t => t.status !== 'COMPLETED').length
  }))
}
const activeCourseGroups = computed(() => buildCourseGroups(t => t.status !== 'COMPLETED'))
const completedCourseGroups = computed(() => buildCourseGroups(t => t.status === 'COMPLETED'))

/* 通用任务：未完成在上、已完成收底 */
const activeGenericTasks = computed(() => tasks.value.filter(t => isGeneric(t) && t.status !== 'COMPLETED').sort(byDueAt))
const completedGenericTasks = computed(() => tasks.value.filter(t => isGeneric(t) && t.status === 'COMPLETED').sort(byDueAt))

const hasActiveTasks = computed(() => activeProjectGroups.value.length || activeCourseGroups.value.length || activeGenericTasks.value.length)
const completedCount = computed(() =>
  completedProjectGroups.value.reduce((s, g) => s + g.total, 0) +
  completedCourseGroups.value.reduce((s, g) => s + g.items.length, 0) +
  completedGenericTasks.value.length
)
const hasCompleted = computed(() => completedCount.value > 0)

/* 按状态视图（原逻辑保留） */
const visible = computed(() => filter.value ? tasks.value.filter(task => task.status === filter.value) : tasks.value)
const statusGroups = computed(() => [
  { id: 'upcoming', title: '待开始', items: visible.value.filter(task => task.status === 'TODO').sort(byDueAt) },
  { id: 'progress', title: '进行中', items: visible.value.filter(task => task.status === 'IN_PROGRESS').sort(byDueAt) },
  { id: 'completed', title: '已完成', items: visible.value.filter(task => task.status === 'COMPLETED').sort(byDueAt) },
].filter(group => group.items.length))
const courseName = id => zh(courses.value.find(course => course.id === id)?.name || '通用任务')

async function load() {
  loading.value = true; error.value = ''
  try {
    const [taskResult, courseResult] = await Promise.allSettled([api.tasks(), api.courses()])
    if (taskResult.status === 'rejected') throw taskResult.reason
    tasks.value = taskResult.value || []
    courses.value = courseResult.status === 'fulfilled' ? (courseResult.value || []) : []
    if (courseResult.status === 'rejected') error.value = `任务已加载，但课程名称加载失败：${courseResult.reason.message}`
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function openCreate() { editingId.value = null; draft.value = blank(); dialog.value = true }
function openEdit(task) { editingId.value = task.id; draft.value = { courseId: task.courseId, title: task.title, description: task.description || '', priority: task.priority, status: task.status, dueAt: task.dueAt || '' }; dialog.value = true }
async function save() { if (!draft.value.title.trim()) return; saving.value = true; try { const payload = { ...draft.value, dueAt: draft.value.dueAt || null }; editingId.value ? await api.updateTask(editingId.value, payload) : await api.createTask(payload); dialog.value = false; ElMessage.success(editingId.value ? '任务已更新' : '任务已创建'); await load() } catch (e) { error.value = e.message } finally { saving.value = false } }
async function complete(task) { busyId.value = task.id; try { await api.completeTask(task.id); ElMessage.success('任务已完成'); await load() } catch (e) { error.value = e.message } finally { busyId.value = '' } }
async function restore(task) { busyId.value = task.id; try { await api.updateTask(task.id, { courseId: task.courseId, title: task.title, description: task.description, dueAt: task.dueAt, status: 'TODO', priority: task.priority }); ElMessage.success('任务已恢复为待开始'); await load() } catch (e) { error.value = e.message } finally { busyId.value = '' } }
async function remove(task) { try { await ElMessageBox.confirm(`确定删除“${zh(task.title)}”吗？`, '删除任务', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return } busyId.value = task.id; try { await api.deleteTask(task.id); ElMessage.success('任务已删除'); await load() } catch (e) { error.value = e.message } finally { busyId.value = '' } }
onMounted(load)
</script>

<template>
  <section>
    <div class="section-heading">
      <div><span class="eyebrow">学习计划</span><h2>学习任务</h2><p>把学习计划拆解为清晰、可跟踪的行动。</p></div>
      <div class="task-actions">
        <div class="view-switch">
          <button :class="{active:viewMode==='grouped'}" @click="viewMode='grouped'">按归属</button>
          <button :class="{active:viewMode==='status'}" @click="viewMode='status'">按状态</button>
        </div>
        <el-select v-if="viewMode==='status'" v-model="filter" placeholder="全部状态" clearable><el-option label="待开始" value="TODO"/><el-option label="进行中" value="IN_PROGRESS"/><el-option label="已完成" value="COMPLETED"/></el-select>
        <el-button type="primary" @click="openCreate">新建任务</el-button>
      </div>
    </div>
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon @close="error=''"/>
    <el-skeleton v-if="loading" :rows="7" animated/>
    <template v-else>
      <!-- 按归属视图 -->
      <template v-if="viewMode==='grouped'">
        <div v-if="!hasActiveTasks && !hasCompleted" class="empty-card"><el-empty description="当前还没有任务。"><el-button type="primary" @click="openCreate">创建任务</el-button></el-empty></div>
        <template v-else>
          <!-- 未完成区 -->
          <template v-if="hasActiveTasks">
            <section v-if="activeProjectGroups.length" class="task-group">
              <div class="group-heading"><h3>项目任务</h3><span>{{activeProjectGroups.length}}</span></div>
              <div v-for="group in activeProjectGroups" :key="group.name" class="project-group">
                <div class="project-header">
                  <div class="project-title"><strong>{{group.name}}</strong><small>{{group.done}} / {{group.total}} 已完成</small></div>
                  <el-progress :percentage="Math.round(group.done/group.total*100)" :stroke-width="8" color="#596be5" style="width:160px;flex:none"/>
                </div>
                <div class="task-list">
                  <article v-for="(task,idx) in group.items" :key="task.id" class="task-card" :class="{done:task.status==='COMPLETED'}">
                    <span class="step-mark task-order" :class="{done:task.status==='COMPLETED'}">{{task.status==='COMPLETED'?'✓':(circled[idx]||idx+1)}}</span>
                    <button class="task-check" :disabled="busyId===task.id" @click="task.status==='COMPLETED'?restore(task):complete(task)">{{task.status==='COMPLETED'?'✓':'○'}}</button>
                    <div class="doc-main"><strong>{{projectInfo(task).title}}</strong><small>项目任务<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
                    <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
                    <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
                  </article>
                </div>
              </div>
            </section>
            <section v-if="activeCourseGroups.length" class="task-group">
              <div class="group-heading"><h3>课程任务</h3><span>{{activeCourseGroups.length}}</span></div>
              <div v-for="group in activeCourseGroups" :key="group.courseId" class="course-group">
                <div class="course-group-header"><strong>{{zh(group.name)}}</strong><el-tag size="small" effect="plain">{{group.pending}} 待办</el-tag></div>
                <div class="task-list">
                  <article v-for="task in group.items" :key="task.id" class="task-card">
                    <button class="task-check" :disabled="busyId===task.id" @click="complete(task)">○</button>
                    <div class="doc-main"><strong>{{zh(task.title)}}</strong><small>{{courseName(task.courseId)}}<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
                    <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
                    <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
                  </article>
                </div>
              </div>
            </section>
            <section v-if="activeGenericTasks.length" class="task-group">
              <div class="group-heading"><h3>通用任务</h3><span>{{activeGenericTasks.length}}</span></div>
              <div class="task-list">
                <article v-for="task in activeGenericTasks" :key="task.id" class="task-card">
                  <button class="task-check" :disabled="busyId===task.id" @click="complete(task)">○</button>
                  <div class="doc-main"><strong>{{zh(task.title)}}</strong><small>通用任务<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
                  <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
                  <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
                </article>
              </div>
            </section>
          </template>

          <!-- 已完成任务折叠区（默认折叠） -->
          <section v-if="hasCompleted" class="task-group completed-section">
            <button class="completed-toggle" @click="completedOpen=!completedOpen">
              <span class="toggle-arrow">{{completedOpen ? '▾' : '▸'}}</span>
              <strong>已完成任务</strong>
              <span class="completed-count">{{completedCount}} 项</span>
            </button>
            <div v-if="completedOpen" class="completed-content">
              <section v-if="completedProjectGroups.length" class="task-group">
                <div class="group-heading"><h3>已完成项目</h3><span>{{completedProjectGroups.length}}</span></div>
                <div v-for="group in completedProjectGroups" :key="group.name" class="project-group complete">
                  <div class="project-header">
                    <div class="project-title"><strong>{{group.name}}</strong><small>{{group.done}} / {{group.total}} 已完成</small></div>
                    <el-progress :percentage="100" :stroke-width="8" color="#45ad7e" style="width:160px;flex:none"/>
                  </div>
                  <div class="task-list">
                    <article v-for="(task,idx) in group.items" :key="task.id" class="task-card done">
                      <span class="step-mark task-order done">✓</span>
                      <button class="task-check" :disabled="busyId===task.id" @click="restore(task)">✓</button>
                      <div class="doc-main"><strong>{{projectInfo(task).title}}</strong><small>项目任务<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
                      <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
                      <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
                    </article>
                  </div>
                </div>
              </section>
              <section v-if="completedCourseGroups.length" class="task-group">
                <div class="group-heading"><h3>课程已完成</h3><span>{{completedCourseGroups.length}}</span></div>
                <div v-for="group in completedCourseGroups" :key="group.courseId" class="course-group">
                  <div class="course-group-header"><strong>{{zh(group.name)}}</strong></div>
                  <div class="task-list">
                    <article v-for="task in group.items" :key="task.id" class="task-card done">
                      <button class="task-check" :disabled="busyId===task.id" @click="restore(task)">✓</button>
                      <div class="doc-main"><strong>{{zh(task.title)}}</strong><small>{{courseName(task.courseId)}}<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
                      <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
                      <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
                    </article>
                  </div>
                </div>
              </section>
              <section v-if="completedGenericTasks.length" class="task-group">
                <div class="group-heading"><h3>通用已完成</h3><span>{{completedGenericTasks.length}}</span></div>
                <div class="task-list">
                  <article v-for="task in completedGenericTasks" :key="task.id" class="task-card done">
                    <button class="task-check" :disabled="busyId===task.id" @click="restore(task)">✓</button>
                    <div class="doc-main"><strong>{{zh(task.title)}}</strong><small>通用任务<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
                    <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
                    <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
                  </article>
                </div>
              </section>
            </div>
          </section>
        </template>
      </template>
      <!-- 按状态视图（原逻辑） -->
      <template v-else>
        <div v-if="!visible.length" class="empty-card"><el-empty description="当前筛选下没有任务。"><el-button type="primary" @click="openCreate">创建任务</el-button></el-empty></div>
        <template v-else><section v-for="group in statusGroups" :key="group.id" class="task-group">
          <div class="group-heading"><h3>{{group.title}}</h3><span>{{group.items.length}}</span></div>
          <div class="task-list"><article v-for="task in group.items" :key="task.id" class="task-card" :class="{done:task.status==='COMPLETED'}">
            <button class="task-check" :disabled="busyId===task.id" @click="task.status==='COMPLETED'?restore(task):complete(task)">{{task.status==='COMPLETED'?'✓':'○'}}</button>
            <div class="doc-main"><strong>{{zh(task.title)}}</strong><small>{{courseName(task.courseId)}}<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
            <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
            <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
          </article></div>
        </section></template>
      </template>
    </template>
    <el-dialog v-model="dialog" :title="editingId?'编辑任务':'新建学习任务'" width="460px"><el-form label-position="top">
      <el-form-item label="标题" required><el-input v-model="draft.title"/></el-form-item>
      <el-form-item label="课程"><el-select v-model="draft.courseId" clearable placeholder="通用任务"><el-option v-for="course in courses" :key="course.id" :label="zh(course.name)" :value="course.id"/></el-select></el-form-item>
      <el-form-item label="说明"><el-input v-model="draft.description" type="textarea" :rows="3"/></el-form-item>
      <div class="form-row"><el-form-item label="优先级"><el-select v-model="draft.priority"><el-option label="低" value="LOW"/><el-option label="中" value="MEDIUM"/><el-option label="高" value="HIGH"/></el-select></el-form-item><el-form-item v-if="editingId" label="状态"><el-select v-model="draft.status"><el-option v-for="value in ['TODO','IN_PROGRESS','COMPLETED']" :key="value" :label="statusText[value]" :value="value"/></el-select></el-form-item></div>
      <el-form-item label="截止时间（ISO-8601）"><el-input v-model="draft.dueAt" placeholder="2026-10-20T12:00:00Z"/></el-form-item>
    </el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!draft.title.trim()" @click="save">保存任务</el-button></template></el-dialog>
  </section>
</template>
