# Final AI Smoke Test

Run this checklist after a valid `ARK_API_KEY` or `OPENAI_API_KEY` and, where needed, `TAVILY_API_KEY` are configured. Do not mark an item complete based on mocks.

- [ ] `GET /api/ai/test` returns HTTP 200 and real model text.
- [ ] Upload `demo-data/Operating Systems Demo Notes.pdf` through the Course UI/API.
- [ ] Document progresses to `READY`.
- [ ] PGVector contains vectors whose metadata has courseId, documentId, filename, sourceType, and real pageNumber.
- [ ] Local RAG answers the `ORION-4729` question correctly.
- [ ] Returned Local source is the uploaded PDF and correct page.
- [ ] AI Quiz generation creates real Quiz and Question rows.
- [ ] Agent autonomously calls `getWeakPoints`.
- [ ] Agent autonomously calls `searchLocalKnowledge`.
- [ ] PlanningSkill returns a structured plan.
- [ ] TaskSkill creates real database tasks after planning.
- [ ] Tavily Web Search returns real external sources.
- [ ] Agent can return Local and Web sources together with distinct types.
- [ ] Weekly Review stores an AI summary based only on calculated statistics.
- [ ] A second course cannot retrieve the first course’s PDF vectors.
- [ ] No final answer claims a side effect when the related Tool failed.
