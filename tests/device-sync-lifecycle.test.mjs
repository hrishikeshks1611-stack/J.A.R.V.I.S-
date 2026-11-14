import test from "node:test";
import assert from "node:assert/strict";
import { hashToken } from "../lib/jarvis-store.js";
import pairHandler from "../api/pair.js";

const originalFetch = globalThis.fetch;
const envKeys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN", "JARVIS_SETUP_CODE"];
const originalEnv = Object.fromEntries(envKeys.map(key => [key, process.env[key]]));

function makeRedis() {
  const values = new Map();
  const sets = new Map();
  const deletedKeys = [];
  const calls = [];
  function execute(command) {
    calls.push(command);
    const [op, ...args] = command;
    if (op === "GET") return values.get(args[0]) ?? null;
    if (op === "GETDEL") {
      const value = values.get(args[0]) ?? null;
      values.delete(args[0]);
      return value;
    }
    if (op === "SET") {
      const [key, value, ...options] = args;
      if (options.includes("NX") && values.has(key)) return null;
      values.set(key, value);
      return "OK";
    }
    if (op === "SADD") {
      const [key, member] = args;
      if (!sets.has(key)) sets.set(key, new Set());
      sets.get(key).add(member);
      return 1;
    }
    if (op === "SMEMBERS") return [...(sets.get(args[0]) || [])];
    if (op === "SREM") {
      sets.get(args[0])?.delete(args[1]);
      return 1;
    }
    if (op === "DEL") {
      for (const key of args) {
        deletedKeys.push(key);
        values.delete(key);
      }
      return args.length;
    }
    if (op === "EXPIRE") return values.has(args[0]) ? 1 : 0;
    if (op === "LPUSH") {
      const list = values.get(args[0]) || [];
      list.unshift(args[1]);
      values.set(args[0], list);
      return list.length;
    }
    if (op === "LTRIM") return "OK";
    if (op === "LRANGE") return values.get(args[0]) || [];
    if (op === "EVAL") {
      const script = args[0];
      const keyCount = Number(args[1]);
      const keys = args.slice(2, 2 + keyCount);
      const argv = args.slice(2 + keyCount);
      if (script.includes("table.insert(history")) {
        const key = keys[0];
        const history = values.has(key) ? JSON.parse(values.get(key)) : [];
        const timestamp = Number(argv[2]);
        history.push({ role: "user", text: argv[0].slice(0, 12000), createdAt: timestamp });
        history.push({ role: "model", text: argv[1].slice(0, 12000), createdAt: timestamp });
        while (history.length > 40) history.shift();
        values.set(key, JSON.stringify(history));
        return JSON.stringify(history);
      }
      if (script.includes("target.role = 'owner'")) {
        const [ownerKey, currentDeviceKey, targetDeviceKey, currentSessionKey, targetSessionKey] = keys;
        const [currentHash, targetHash, ttl] = argv;
        if (values.get(ownerKey) !== currentHash) return JSON.stringify({ ok: false, reason: "owner_changed" });
        const currentDevice = JSON.parse(values.get(currentDeviceKey));
        const targetDevice = JSON.parse(values.get(targetDeviceKey));
        const currentSession = JSON.parse(values.get(currentSessionKey));
        const targetSession = JSON.parse(values.get(targetSessionKey));
        if (!currentDevice || !targetDevice || !currentSession || !targetSession) return JSON.stringify({ ok: false, reason: "session_missing" });
        if (targetDevice.sessionHash !== targetHash || targetDevice.deviceId === currentDevice.deviceId) return JSON.stringify({ ok: false, reason: "target_changed" });
        currentDevice.role = "device";
        currentSession.role = "device";
        targetDevice.role = "owner";
        targetSession.role = "owner";
        values.set(currentDeviceKey, JSON.stringify(currentDevice));
        values.set(targetDeviceKey, JSON.stringify(targetDevice));
        values.set(currentSessionKey, JSON.stringify(currentSession));
        values.set(targetSessionKey, JSON.stringify(targetSession));
        values.set(ownerKey, targetHash);
        return JSON.stringify({ ok: true, ownerDeviceId: targetDevice.deviceId, previousOwnerDeviceId: currentDevice.deviceId });
      }
      throw new Error("Unexpected Redis script in integration test");
    }
    throw new Error("Unexpected Redis command: " + command.join(" "));
  }
  globalThis.fetch = async (_url, options) => {
    const commands = JSON.parse(options.body);
    const results = commands.map(command => ({ result: execute(command) }));
    return new Response(JSON.stringify(results), { status: 200, headers: { "content-type": "application/json" } });
  };
  return { values, sets, deletedKeys, calls };
}

function response() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { this.ended = true; return this; }
  };
}

async function request(body, token) {
  const res = response();
  await pairHandler({
    method: "POST",
    headers: token ? { authorization: "Bearer " + token } : {},
    body
  }, res);
  return res;
}

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  for (const key of envKeys) {
    if (originalEnv[key] === undefined) delete process.env[key];
    else process.env[key] = originalEnv[key];
  }
});

test("15.0 device-sync lifecycle preserves shared history through pairing, owner transfer, and 14.0 session revocation", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.JARVIS_SETUP_CODE = "test-owner-setup-code";
  const redis = makeRedis();

  const ownerClaim = await request({ action: "claim", setupCode: process.env.JARVIS_SETUP_CODE, deviceName: "JARVIS 14.0" });
  assert.equal(ownerClaim.statusCode, 200);
  const oldToken = ownerClaim.body.token;
  const oldDeviceId = ownerClaim.body.deviceId;
  assert.equal(ownerClaim.body.role, "owner");

  const codeResult = await request({ action: "create-code" }, oldToken);
  assert.equal(codeResult.statusCode, 200);
  assert.equal(codeResult.body.expiresIn, 600);

  const newPair = await request({ action: "pair", pairingCode: codeResult.body.code, deviceName: "JARVIS 15.0" });
  assert.equal(newPair.statusCode, 200);
  const newToken = newPair.body.token;
  const newDeviceId = newPair.body.deviceId;
  assert.notEqual(newDeviceId, oldDeviceId);
  assert.equal(newPair.body.role, "device");

  const pairedRole = await request({ action: "verify" }, newToken);
  assert.equal(pairedRole.statusCode, 200);
  assert.equal(pairedRole.body.role, "device");

  const localAppend = await request({ action: "append-local", message: "15.0 shared-sync check", reply: "History is shared." }, newToken);
  assert.equal(localAppend.statusCode, 200);
  const oldHistory = await request({ action: "history" }, oldToken);
  const newHistory = await request({ action: "history" }, newToken);
  assert.deepEqual(newHistory.body.messages, oldHistory.body.messages);
  assert.equal(newHistory.body.messages.at(-1).text, "History is shared.");

  const transfer = await request({ action: "transfer-owner", deviceId: newDeviceId }, oldToken);
  assert.equal(transfer.statusCode, 200);
  assert.equal(transfer.body.transferred, true);

  const newOwnerVerify = await request({ action: "verify" }, newToken);
  const oldDeviceVerify = await request({ action: "verify" }, oldToken);
  assert.equal(newOwnerVerify.statusCode, 200);
  assert.equal(newOwnerVerify.body.role, "owner");
  assert.equal(oldDeviceVerify.statusCode, 200);
  assert.equal(oldDeviceVerify.body.role, "device");

  const revoked = await request({ action: "revoke", deviceId: oldDeviceId }, newToken);
  assert.equal(revoked.statusCode, 200);
  assert.equal(revoked.body.revoked, true);

  const oldAccess = await request({ action: "verify" }, oldToken);
  assert.equal(oldAccess.statusCode, 401);
  const newOwnerStillValid = await request({ action: "verify" }, newToken);
  assert.equal(newOwnerStillValid.statusCode, 200);
  assert.equal(newOwnerStillValid.body.role, "owner");

  const preservedHistory = await request({ action: "history" }, newToken);
  assert.equal(preservedHistory.statusCode, 200);
  assert.equal(preservedHistory.body.messages.at(-1).text, "History is shared.");

  assert.ok(redis.deletedKeys.includes("jarvis:v1:session:" + hashToken(oldToken)));
  assert.ok(redis.deletedKeys.includes("jarvis:v1:device:" + oldDeviceId));
  assert.ok(!redis.deletedKeys.includes("jarvis:v1:conversation:main"));
  assert.ok(!redis.deletedKeys.includes("jarvis:v1:memory:main"));
});
