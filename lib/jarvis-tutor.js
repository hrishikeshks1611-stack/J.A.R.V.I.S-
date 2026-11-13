const clean = (value, max = 300) => String(value ?? "").replace(/[\r\n\u0000-\u001f]/g, " ").trim().slice(0, max);

export function isTutorRequest(message) {
  const text = String(message || "").toLowerCase();
  return /\b(teach me|tutor me|explain this|explain the topic|help me study|study for|exam|examination|revision|revise|mock test|practice questions|quiz me|test me|solve this question|step by step solution)\b/.test(text) ||
    /\b(create|make|build|prepare)\b.{0,50}\b(study plan|revision plan|exam plan|question paper|practice quiz)\b/.test(text);
}

export function buildTutorSystemInstruction() {
  return [
    "You are now using JARVIS Tutor Engine, an exam-focused personal tutor for school, college, university and competitive-exam learning.",
    "First identify the learner's requested subject, topic, level and exam goal from the conversation; ask only one concise clarification if an essential detail is missing.",
    "Teach concepts accurately in clear steps, define technical terms, use worked examples, and adapt depth to the learner's level. Do not merely dump notes.",
    "For exam preparation, organise responses as: key concepts, exam-relevant explanation, a structured answer suitable for the requested mark length, common mistakes, and a short active-recall check when useful.",
    "For practice, ask one question at a time when interactive testing is requested; wait for the learner's answer, grade it against a clear rubric, explain errors constructively, and track weak topics only within available conversation context.",
    "When asked to solve a problem, show the method and intermediate steps, then give the final answer. Distinguish facts from assumptions and never invent syllabus, marking scheme, past-paper questions, or official exam dates.",
    "If the board, university, course, year, or syllabus materially changes the answer, ask for that one missing detail or clearly state the assumption.",
    "Support revision timetables, flashcards, summaries, sample questions, mock exams and last-minute revision, but do not claim that a study plan creates reminders unless a real reminder tool confirms it."
  ].join(" ");
}

export function buildExamRevisionPlan({ subject, examDate, topics = [], minutesPerDay = 60, today = new Date().toISOString().slice(0, 10) } = {}) {
  const cleanSubject = clean(subject, 100);
  if (!cleanSubject) throw new Error("A subject is required.");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(examDate || ""))) throw new Error("Provide the exam date as YYYY-MM-DD.");
  const start = new Date(`${today}T12:00:00Z`);
  const end = new Date(`${examDate}T12:00:00Z`);
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime())) throw new Error("The exam date is invalid.");
  const days = Math.ceil((end - start) / 86400000);
  if (days < 1 || days > 365) throw new Error("The exam date must be between tomorrow and one year from today.");
  const minutes = Number(minutesPerDay);
  if (!Number.isInteger(minutes) || minutes < 15 || minutes > 360) throw new Error("Daily study time must be between 15 and 360 minutes.");
  const cleanTopics = (Array.isArray(topics) ? topics : []).map(item => clean(item, 100)).filter(Boolean).slice(0, 20);
  const units = cleanTopics.length ? cleanTopics : ["Core concepts", "Important applications", "Practice and weak areas"];
  const sessions = [];
  for (let day = 0; day < days; day++) {
    const date = new Date(start.getTime() + (day + 1) * 86400000).toISOString().slice(0, 10);
    const isFinal = day === days - 1;
    const isReview = day % 4 === 3 || isFinal;
    sessions.push({
      date,
      focus: isFinal ? "Light final review and rest" : isReview ? "Active recall and mixed practice" : units[day % units.length],
      minutes: isFinal ? Math.min(30, minutes) : minutes,
      task: isFinal ? "Review a one-page summary, prepare exam materials, and avoid cramming new topics." : isReview ? "Answer questions without notes, mark mistakes, and revisit weak concepts." : `Study ${units[day % units.length]}, then solve a few exam-style questions.`
    });
  }
  return { ok: true, type: "exam_revision_plan", subject: cleanSubject, examDate, daysUntilExam: days, minutesPerDay: minutes, topics: units, sessions, guidance: "This is a flexible plan, not a guarantee of exam results. Adjust it to your official syllabus and available past papers." };
}
