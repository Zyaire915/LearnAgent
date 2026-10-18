import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
const navigationNoise = new Set(['搜索','写随笔','我的博客','短消息','简洁模式','用户头像','返回主页','登录','注册','上一页','下一页'])

function isPipeRow(line) {
  const value = line.trim()
  return value.startsWith('|') && value.endsWith('|') && value.split('|').length >= 4
}

function isSeparatorRow(line) {
  if (!isPipeRow(line)) return false
  const cells = line.trim().slice(1, -1).split('|').map(value => value.trim())
  return cells.length > 1 && cells.every(value => /^:?-{3,}:?$/.test(value))
}

function tableSeparator(line) {
  const columns = Math.max(2, line.trim().slice(1, -1).split('|').length)
  return `|${Array.from({ length: columns }, () => ' --- ').join('|')}|`
}

export function normalizeMarkdown(value = '') {
  const source = String(value).normalize('NFC').replaceAll('\uFFFD', '').replaceAll('\u00a0', ' ')
  const input = source.split(/\r?\n/)
  const cleaned = []
  let previous = ''
  for (const line of input) {
    const trimmed = line.trim()
    if (navigationNoise.has(trimmed) || (trimmed && trimmed === previous && trimmed.length < 30)) continue
    cleaned.push(line)
    if (trimmed) previous = trimmed
  }
  const output = []
  let insideTable = false
  let insideFence = false
  for (let index = 0; index < cleaned.length; index += 1) {
    const line = cleaned[index]
    output.push(line)
    if (/^\s*(```|~~~)/.test(line)) {
      insideFence = !insideFence
      insideTable = false
      continue
    }
    if (insideFence) continue
    if (!isPipeRow(line)) {
      insideTable = false
      continue
    }
    if (!insideTable && isPipeRow(cleaned[index + 1] || '')) {
      if (!isSeparatorRow(cleaned[index + 1])) output.push(tableSeparator(line))
      insideTable = true
    }
  }
  return output.join('\n')
}

export function renderMarkdown(value = '') {
  return DOMPurify.sanitize(markdown.render(normalizeMarkdown(value)), {
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i,
  })
}
