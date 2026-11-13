const DAYS = Object.freeze(["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]);

function boundedText(value, label, max) {
  const text = String(value ?? "").replace(/[\r\n\u0000-\u001f]/g, " ").trim().slice(0, max);
  if (!text) throw new Error(`A ${label} is required.`);
  return text;
}

function distributeDays(count) {
  if (count === 7) return [...DAYS];
  return Array.from({ length: count }, (_, index) => DAYS[Math.floor(index * 7 / count)]);
}

/**
 * Build a realistic, repeatable weekly study plan. This is a planning aid,
 * not a claim that learning or reminders happen automatically.
 */
export function buildStudyPlan({ subject, goal, daysPerWeek = 5, minutesPerSession = 45, level = "beginner" } = {}) {
  const cleanSubject = boundedText(subject, "subject", 100);
  const cleanGoal = boundedText(goal, "learning goal", 300);
  const days = Number(daysPerWeek);
  const minutes = Number(minutesPerSession);
  const difficulty = String(level || "beginner").toLowerCase();

  if (!Number.isInteger(days) || days < 1 || days > 7) {
    throw new Error("Choose between 1 and 7 study days per week.");
  }
  if (!Number.isInteger(minutes) || minutes < 15 || minutes > 180) {
    throw new Error("Each study session must be between 15 and 180 minutes.");
  }
  if (!["beginner", "intermediate", "advanced"].includes(difficulty)) {
    throw new Error("Choose beginner, intermediate, or advanced as the level.");
  }

  const retrieval = Math.max(3, Math.floor(minutes * 0.12));
  const concept = Math.max(4, Math.floor(minutes * (difficulty === "beginner" ? 0.40 : 0.32)));
  const practice = Math.max(4, Math.floor(minutes * (difficulty === "advanced" ? 0.40 : 0.35)));
  const reflection = minutes - retrieval - concept - practice;
  const sessionDays = distributeDays(days);

  const sessions = sessionDays.map((day, index) => ({
    day,
    focus: index === 0 ? "Build the foundation" : index === days - 1 ? "Review and test understanding" : "Learn, practise, and connect ideas",
    activities: [
      { name: "Active recall", minutes: retrieval, instruction: "Without looking at notes, recall what you already know." },
      { name: "Learn", minutes: concept, instruction: `Study one manageable part of ${cleanSubject}; explain the key idea in your own words.` },
      { name: "Practice", minutes: practice, instruction: difficulty === "beginner" ? "Work through guided examples, then try one independently." : "Solve a challenging problem or apply the idea to a new case." },
      { name: "Review", minutes: reflection, instruction: "Write a short summary, note uncertainties, and choose the next small step." }
    ]
  }));

  return {
    ok: true,
    type: "weekly_study_plan",
    subject: cleanSubject,
    goal: cleanGoal,
    level: difficulty,
    daysPerWeek: days,
    minutesPerSession: minutes,
    totalMinutesPerWeek: days * minutes,
    sessions,
    spacedReview: [
      { afterDays: 1, task: "Recall the key ideas without notes." },
      { afterDays: 3, task: "Answer practice questions and revisit mistakes." },
      { afterDays: 7, task: "Complete a short cumulative self-test." }
    ],
    guidance: "Treat this as a flexible plan. Move a session when needed; consistency and active recall matter more than perfect attendance."
  };
}
