import test from "node:test";
import assert from "node:assert/strict";
import { isTutorRequest, buildTutorSystemInstruction, buildExamRevisionPlan } from "../lib/jarvis-tutor.js";
import { isMedicalRequest, detectMedicalEmergency, buildMedicalEmergencyReply, buildMedicalSystemInstruction } from "../lib/jarvis-medical.js";

test("Tutor Engine recognises learning and exam requests and has an exam-focused teaching contract", () => {
  assert.equal(isTutorRequest("Teach me accounting for my exam"), true);
  assert.equal(isTutorRequest("Quiz me on business law"), true);
  assert.equal(isTutorRequest("Explain accounting"), true);
  assert.equal(isTutorRequest("What time is it?"), false);
  const instruction = buildTutorSystemInstruction();
  assert.match(instruction, /active-recall/i);
  assert.match(instruction, /mark.*responses|grade/i);
  assert.match(instruction, /never invent syllabus/i);
});

test("exam revision planner distributes syllabus topics and validates dates and study time", () => {
  const plan = buildExamRevisionPlan({ subject: "Accounting", examDate: "2026-11-17", today: "2026-11-13", topics: ["Ledger", "Trial balance"], minutesPerDay: 60 });
  assert.equal(plan.daysUntilExam, 4);
  assert.equal(plan.sessions.length, 4);
  assert.equal(plan.sessions[0].focus, "Ledger");
  assert.equal(plan.sessions.at(-1).focus, "Light final review and rest");
  assert.throws(() => buildExamRevisionPlan({ subject: "", examDate: "2026-11-17", today: "2026-11-13" }), /subject is required/i);
  assert.throws(() => buildExamRevisionPlan({ subject: "Maths", examDate: "2026-11-13", today: "2026-11-13" }), /between tomorrow/);
  assert.throws(() => buildExamRevisionPlan({ subject: "Maths", examDate: "2026-11-17", today: "2026-11-13", minutesPerDay: 5 }), /15 and 360/);
});

test("Medical Assistant recognises health requests and prioritises acute red flags", () => {
  assert.equal(isMedicalRequest("What are common causes of fever?"), true);
  assert.equal(isMedicalRequest("I have a headache"), true);
  assert.equal(detectMedicalEmergency("My father is having chest pain right now"), true);
  assert.equal(detectMedicalEmergency("Someone is not breathing, help"), true);
  assert.equal(detectMedicalEmergency("My dad cannot breathe"), true);
  assert.equal(detectMedicalEmergency("Explain the symptoms of stroke for my exam"), false);
  assert.equal(detectMedicalEmergency("What is chest pain?"), false);
  const reply = buildMedicalEmergencyReply();
  assert.match(reply, /Call 112 now/);
  assert.match(reply, /108 for an ambulance in Kerala/);
  assert.match(reply, /cannot call an ambulance/);
  const instruction = buildMedicalSystemInstruction();
  assert.match(instruction, /not a diagnosis/i);
  assert.match(instruction, /Never prescribe/i);
});
