import test from "node:test";
import assert from "node:assert/strict";
import { createTaskPlan, updateTaskStep, taskProgress, attachTaskEvidence } from "../lib/jarvis-task-engine.js";

test("task plans validate bounded steps, unique IDs, and dependency graphs", () => {
  const plan = createTaskPlan({ goal: "Prepare a verified research brief", steps: [
    { id: "research", title: "Gather reliable sources" },
    { id: "analyse", title: "Analyse evidence", dependsOn: ["research"] },
    { id: "draft", title: "Prepare the brief", dependsOn: ["analyse"] }
  ] });
  assert.equal(plan.steps.length, 3);
  assert.equal(plan.status, "in_progress");
  assert.throws(() => createTaskPlan({ goal: "x", steps: [
    { id: "a", title: "A", dependsOn: ["b"] },
    { id: "b", title: "B", dependsOn: ["a"] }
  ] }), /cycle/i);
  assert.throws(() => createTaskPlan({ goal: "x", steps: [
    { id: "a", title: "A" }, { id: "a", title: "Duplicate" }
  ] }), /unique/i);
});

test("task steps cannot bypass dependencies or claim completion without evidence", () => {
  let plan = createTaskPlan({ goal: "Prepare a report", steps: [
    { id: "collect", title: "Collect data" },
    { id: "write", title: "Write report", dependsOn: ["collect"] }
  ] });
  const attached = attachTaskEvidence(plan, { tool: "search_conversation", summary: "Retrieved the previous research notes for this report." });
  plan = attached.plan;
  assert.throws(() => updateTaskStep(plan, { stepId: "write", status: "completed", evidence: "The report was written and checked.", evidenceRef: attached.evidence.id }), /prerequisite/i);
  assert.throws(() => updateTaskStep(plan, { stepId: "collect", status: "completed", evidence: "Collected 12 verified entries from the source dataset.", evidenceRef: "invented-reference" }), /evidence reference/i);
  assert.throws(() => updateTaskStep(plan, { stepId: "collect", status: "completed", evidence: "done", evidenceRef: attached.evidence.id }), /evidence/i);
  plan = updateTaskStep(plan, { stepId: "collect", status: "completed", evidence: "Collected 12 verified entries from the source dataset.", evidenceRef: attached.evidence.id });
  const draftEvidence = attachTaskEvidence(plan, { tool: "create_study_plan", summary: "The draft report content was generated and returned by the approved planning tool." });
  plan = updateTaskStep(draftEvidence.plan, { stepId: "write", status: "completed", evidence: "Report generated and checked against all 12 source entries.", evidenceRef: draftEvidence.evidence.id });
  assert.equal(plan.status, "completed");
  assert.deepEqual(taskProgress(plan), { completed: 2, total: 2, percent: 100 });
});
