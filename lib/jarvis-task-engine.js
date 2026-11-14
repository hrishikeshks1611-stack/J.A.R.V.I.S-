import { randomUUID } from "node:crypto";

const MAX_STEPS = 8;
const MAX_EVIDENCE_REFS = 12;
const clean = (value, max) => String(value ?? "").replace(/[\r\n\u0000-\u001f]/g, " ").trim().slice(0, max);

export function createTaskPlan({ goal, steps }) {
  const objective = clean(goal, 500);
  if (!objective) throw new Error("A clear task goal is required.");
  if (!Array.isArray(steps) || steps.length < 2 || steps.length > MAX_STEPS) {
    throw new Error("A task plan must contain between 2 and 8 steps.");
  }
  const ids = new Set();
  const normalized = steps.map((step, index) => {
    const id = clean(step?.id, 32).toLowerCase();
    const title = clean(step?.title, 180);
    if (!/^[a-z0-9][a-z0-9_-]{0,31}$/.test(id) || ids.has(id) || !title) {
      throw new Error("Each task step needs a unique short ID and a title.");
    }
    ids.add(id);
    return { id, title, dependsOn: Array.isArray(step.dependsOn) ? step.dependsOn.map(x => clean(x, 32).toLowerCase()) : [], status: "pending", evidence: "" };
  });
  for (const step of normalized) {
    if (step.dependsOn.includes(step.id) || step.dependsOn.some(id => !ids.has(id))) {
      throw new Error("Task dependencies must reference other existing steps, not themselves.");
    }
  }
  const visiting = new Set(), visited = new Set();
  const byId = new Map(normalized.map(step => [step.id, step]));
  function visit(id) {
    if (visiting.has(id)) throw new Error("The task plan contains a dependency cycle.");
    if (visited.has(id)) return;
    visiting.add(id);
    for (const dependency of byId.get(id).dependsOn) visit(dependency);
    visiting.delete(id); visited.add(id);
  }
  for (const step of normalized) visit(step.id);
  const now = Date.now();
  return {
    id: randomUUID(), goal: objective, status: "in_progress",
    createdAt: now, updatedAt: now, evidenceRefs: [], steps: normalized
  };
}

export function attachTaskEvidence(plan, { tool, summary }) {
  if (!plan || !Array.isArray(plan.steps)) throw new Error("The task plan was not found.");
  if (!Array.isArray(plan.evidenceRefs)) plan.evidenceRefs = [];
  const evidence = {
    id: randomUUID(),
    tool: clean(tool, 64),
    summary: clean(summary, 600),
    createdAt: Date.now()
  };
  if (!evidence.tool || !evidence.summary) throw new Error("Task evidence must identify a successful tool and its result.");
  plan.evidenceRefs.push(evidence);
  plan.evidenceRefs = plan.evidenceRefs.slice(-MAX_EVIDENCE_REFS);
  plan.updatedAt = Date.now();
  return { plan, evidence };
}

export function updateTaskStep(plan, { stepId, status, evidence, evidenceRef }) {
  if (!plan || !Array.isArray(plan.steps)) throw new Error("The task plan was not found.");
  const step = plan.steps.find(item => item.id === clean(stepId, 32).toLowerCase());
  if (!step) throw new Error("That step does not belong to this task.");
  if (!["in_progress", "completed", "blocked"].includes(status)) throw new Error("Unsupported task-step status.");
  const proof = clean(evidence, 1000);
  if (status === "completed") {
    if (proof.length < 12) throw new Error("A step cannot be marked complete without concrete verification evidence.");
    if (!Array.isArray(plan.evidenceRefs) || !plan.evidenceRefs.some(item => item.id === String(evidenceRef || ""))) {
      throw new Error("A step can be completed only with an evidence reference returned by a successful approved tool.");
    }
    const unfinished = step.dependsOn.filter(id => plan.steps.find(item => item.id === id)?.status !== "completed");
    if (unfinished.length) throw new Error("Complete prerequisite steps first: " + unfinished.join(", ") + ".");
  }
  step.status = status;
  step.evidence = proof;
  step.evidenceRef = status === "completed" ? String(evidenceRef) : "";
  plan.updatedAt = Date.now();
  if (plan.steps.every(item => item.status === "completed")) plan.status = "completed";
  else if (plan.steps.some(item => item.status === "blocked")) plan.status = "blocked";
  else plan.status = "in_progress";
  return plan;
}

export function taskProgress(plan) {
  const total = Array.isArray(plan?.steps) ? plan.steps.length : 0;
  const completed = Array.isArray(plan?.steps) ? plan.steps.filter(step => step.status === "completed").length : 0;
  return { completed, total, percent: total ? Math.round(completed / total * 100) : 0 };
}
