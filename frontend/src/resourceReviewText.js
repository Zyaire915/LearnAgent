// Presentation only: keep the stored review evidence intact for auditing.
export function formatReviewReason(value = '') {
  return value
    .replace(/^规则\s+R\d+\s*[；;]\s*/, '')
    .replace(/（不代表文章质量已核实）/g, '')
    .replace(/；正文依据：[\s\S]*?(?=；正文抓取时间：|$)/g, '')
    .replace(/；(?:原文作者：|原文发布机构：|原文发布日期：|正文未提取到明确作者|正文未提取到明确发布机构|正文未提取到有效发布日期)[\s\S]*$/, '')
    .split('；')
    .filter(part => !/^(?:作者\/机构信息未知|提供方报告可访问|提供方报告不可访问|可访问性|互动数据|提供方互动指标)/.test(part.trim()))
    .map(part => part.replace(/^正文AI审核：/, '通过内容判断文章质量：'))
    .join('；')
}
