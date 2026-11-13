const RED_FLAGS = [
  /\b(chest pain|pressure in (my|the) chest|heart attack)\b/i,
  /\b(can't breathe|cannot breathe|difficulty breathing|struggling to breathe|not breathing|stopped breathing)\b/i,
  /\b(unconscious|unresponsive|won't wake|not waking up)\b/i,
  /\b(stroke|face droop|slurred speech|sudden weakness|one[- ]sided weakness)\b/i,
  /\b(severe bleeding|bleeding heavily|won't stop bleeding)\b/i,
  /\b(seizure|convulsion)\b/i,
  /\b(anaphylaxis|throat (is )?swelling|tongue (is )?swelling|severe allergic reaction)\b/i,
  /\b(overdose|poisoned|swallowed (bleach|chemical|poison|pesticide))\b/i,
  /\b(suicide attempt|attempted suicide|self[- ]harm emergency)\b/i
];

export function detectMedicalEmergency(message) {
  const text = String(message || "").trim();
  if (!text) return false;
  if (/^(explain|describe|define|list)\b/i.test(text) || /\b(for my exam|for an exam|for a test|as a study topic)\b/i.test(text)) return false;
  const urgentContext = /\b(now|right now|urgent|emergency|help|happening|having|has|have|is having|someone|my|he|she|they|not breathing|unconscious|overdose)\b/i.test(text);
  return urgentContext && RED_FLAGS.some(pattern => pattern.test(text));
}

export function buildMedicalEmergencyReply() {
  return [
    "Sir, this may be a life-threatening medical emergency. Do not wait for an online assessment.",
    "Call 112 now (India's integrated emergency number) or 108 for an ambulance in Kerala. Put the phone on speaker and follow the emergency operator's instructions.",
    "If the person is unconscious or not breathing normally, tell the operator immediately. Do not give food, drink or medication to an unconscious person. Do not move someone with a possible serious neck or back injury unless they are in immediate danger.",
    "For suspected poisoning or overdose, do not induce vomiting; keep the container or packaging for medical staff.",
    "If you are alone, call emergency services first and ask someone nearby to help. I cannot call an ambulance or diagnose the condition for you.",
    "If it is safe to reply, tell me your immediate situation and whether emergency services have been called—but please make that call first."
  ].join("\n\n");
}

export function buildMedicalSystemInstruction() {
  return [
    "You are JARVIS Medical Assistant Engine. Provide cautious, evidence-informed health education and first-aid information, not a diagnosis or substitute for a licensed clinician.",
    "Always check for time-critical red flags. For chest pain, breathing difficulty, stroke signs, unconsciousness, severe bleeding, seizure, anaphylaxis, poisoning, overdose, or imminent self-harm, direct the user to emergency services immediately; do not delay with questions, disclaimers, or lengthy explanations.",
    "For India, 112 is the integrated emergency number; in Kerala, 108 is the ambulance service. Do not claim to have called either service. Do not automatically place calls or contact family.",
    "For non-emergency concerns, ask only the most important missing question, explain possibilities without asserting a diagnosis, describe warning signs that require urgent care, and recommend an appropriate clinician when needed.",
    "Never prescribe, change, stop, or calculate a personalised dose of prescription medicine. Do not recommend combining medicines without checking with a pharmacist or clinician. Encourage users to follow their prescription and official patient leaflet.",
    "For medication questions, request the exact medicine name, strength, formulation, age group and relevant context when essential; flag allergies, pregnancy, children, chronic illness and interactions as reasons to consult a clinician or pharmacist. Do not guess from a brand name alone.",
    "For first aid, provide only established low-risk steps and prioritise emergency services. Do not induce vomiting after poisoning, do not put anything in the mouth during a seizure, and do not give food or drink to an unconscious person.",
    "Be calm, direct and non-judgmental. State uncertainty plainly, distinguish general information from medical advice, and never fabricate clinical references or claim that symptoms are harmless."
  ].join(" ");
}

export function isMedicalRequest(message) {
  return /\b(medical|health|symptom|first aid|medicine|medication|dose|dosage|side effect|pain|fever|injury|illness|doctor|hospital|emergency|headache|dizziness|rash|cough|cold|nausea|vomit|diarrh?ea|blood pressure|blood sugar|diabetes|allergy|allergic|infection|pregnan|anxiety|depression|stroke|seizure|breathing)\b/i.test(String(message || ""));
}
