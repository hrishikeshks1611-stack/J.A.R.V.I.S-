import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import chat from "./api/chat.js";
import pair from "./api/pair.js";
import speech from "./api/speech.js";
import memory from "./api/memory.js";
import backup from "./api/backup.js";

const root = path.dirname(fileURLToPath(import.meta.url));
const routes = new Map([["/api/chat",chat],["/api/pair",pair],["/api/speech",speech],["/api/memory",memory],["/api/backup",backup]]);
const mime = new Map([[".html","text/html; charset=utf-8"],[".js","text/javascript; charset=utf-8"],[".css","text/css; charset=utf-8"],[".json","application/json; charset=utf-8"],[".webmanifest","application/manifest+json"],[".svg","image/svg+xml"],[".png","image/png"],[".jpg","image/jpeg"],[".jpeg","image/jpeg"],[".ico","image/x-icon"]]);

function adapt(res) {
  res.status = code => { res.statusCode=code; return res; };
  res.json = value => { if(!res.headersSent) res.setHeader("Content-Type","application/json; charset=utf-8"); res.end(JSON.stringify(value)); return res; };
  return res;
}
async function body(req) {
  const chunks=[]; let size=0;
  for await (const chunk of req) { size+=chunk.length; if(size>5*1024*1024) throw Object.assign(new Error("Request body exceeds 5 MB."),{status:413}); chunks.push(chunk); }
  if(!chunks.length) return {};
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8")); }
  catch { throw Object.assign(new Error("Request body must be valid JSON."),{status:400}); }
}
async function staticFile(req,res,pathname) {
  let decoded; try { decoded=decodeURIComponent(pathname); } catch { res.writeHead(400); res.end("Bad request"); return; }
  const relative=decoded==="/"?"index.html":decoded.replace(/^\/+/,"");
  const target=path.resolve(root,relative);
  if((!target.startsWith(root+path.sep)&&target!==path.join(root,"index.html")) || relative.split(/[\\/]/).some(x=>x.startsWith(".")||["api","lib","tests"].includes(x))) { res.writeHead(404); res.end("Not found"); return; }
  try {
    const info=await stat(target); if(!info.isFile()) throw new Error("not file");
    const data=await readFile(target);
    res.writeHead(200,{"Content-Type":mime.get(path.extname(target).toLowerCase())||"application/octet-stream","Content-Length":data.length,"Cache-Control":relative==="index.html"?"no-cache":"public, max-age=3600"});
    res.end(data);
  } catch { res.writeHead(404); res.end("Not found"); }
}
const server=createServer(async(req,res)=>{
  const url=new URL(req.url||"/","http://localhost");
  if(req.method==="GET"&&url.pathname==="/healthz"){res.writeHead(200,{"Content-Type":"application/json","Cache-Control":"no-store"});res.end(JSON.stringify({ok:true,service:"jarvis-staging",version:"6.0.0"}));return;}
  const handler=routes.get(url.pathname);
  if(handler){
    try { req.body=["POST","PUT","PATCH"].includes(req.method)?await body(req):{}; await handler(req,adapt(res)); if(!res.writableEnded)res.end(); }
    catch(error){console.error("JARVIS API adapter error:",error);if(!res.headersSent){res.writeHead(error.status||500,{"Content-Type":"application/json"});res.end(JSON.stringify({error:error.status===413||error.status===400?error.message:"JARVIS encountered a server error."}));}else res.end();}
    return;
  }
  if(req.method!=="GET"&&req.method!=="HEAD"){res.writeHead(405,{Allow:"GET, HEAD"});res.end();return;}
  await staticFile(req,res,url.pathname);
});
const port=Number(process.env.PORT||3000);
server.listen(port,"0.0.0.0",()=>console.log("JARVIS staging server listening on "+port));
