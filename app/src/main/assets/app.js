const $ = id => document.getElementById(id);
const KEY='editable-chat-mobile-v1';
let state;
try { state=JSON.parse(localStorage.getItem(KEY)) || {}; }catch {state={};}
state.messages=Array.isArray(state.messages)?state.messages:[];
state.provider=state.provider||'openai';
state.model=state.model||'';
state.mode=state.mode!==false;
let editing=-1, busy=false;
function save(){localStorage.setItem(KEY,JSON.stringify(state));}
function setMode(){ $('modeStatus').textContent=state.mode?'ON':'OFF';$('mode').checked=state.mode; }
function make(tag,cls,text){const el=document.createElement(tag);if(cls)el.className=cls;if(text!==undefined)el.textContent=text;return el;}
function render(){const chat=$('chat');chat.replaceChildren();if(!state.messages.length){const intro=make('div','intro');intro.innerHTML='<div class="logo">✦</div><h2>What should we talk about?</h2><p>Send a message to start. Edit an AI reply anytime and the next request can use your changed version.</p>';chat.append(intro);}state.messages.forEach((m,i)=>{const article=make('article','message '+m.role);const body=make('div','bubble',m.content);article.append(body);if(m.role==='assistant'){const actions=make('div','actions');const edit=make('button',null,'✎ Edit');edit.setAttribute('aria-label','Edit response');edit.onclick=()=>{editing=i;$('editedText').value=m.content;$('editor').showModal();};actions.append(edit);const copy=make('button',null,'▢ Copy');copy.onclick=()=>navigator.clipboard?.writeText(m.content);actions.append(copy);if(m.edited)actions.append(make('span','edited','Edited'));article.append(actions);}chat.append(article);});chat.scrollTop=chat.scrollHeight;}
$('provider').value=state.provider;$('model').value=state.model;setMode();render();
function loadKey(){ $('apiKey').value=AndroidBridge.getKey(state.provider); }
loadKey();
$('saveKey').onclick=()=>{ AndroidBridge.saveKey(state.provider,$('apiKey').value);$('apiKey').value='';alert('API key saved securely on this device.');};
const callbacks=new Map();let reqId=0;
window.nativeChatResult=(id,ok,payload)=>{const cb=callbacks.get(id);if(!cb)return;callbacks.delete(id);ok?cb.resolve(payload):cb.reject(new Error(payload));};
function providerChat(messages){return new Promise((resolve,reject)=>{const id=++reqId;callbacks.set(id,{resolve,reject});AndroidBridge.chat(id,JSON.stringify({provider:state.provider,model:state.model,messages}));});}
$('mode').onchange=e=>{state.mode=e.target.checked;setMode();save();};
$('settings').onclick=$('menu').onclick=()=>{$('config').classList.toggle('hidden');};
$('done').onclick=()=>{$('config').classList.add('hidden');};
$('provider').onchange=e=>{state.provider=e.target.value;save();loadKey();};
$('model').oninput=e=>{state.model=e.target.value;save();};
$('new').onclick=()=>{if(confirm('Start a new conversation?')){state.messages=[];save();render();}};
$('plus').onclick=()=>{$('input').focus();};
$('cancel').onclick=()=>$('editor').close();
$('editForm').onsubmit=e=>{e.preventDefault();if(editing>=0 && state.messages[editing]){state.messages[editing].content=$('editedText').value;state.messages[editing].edited=true;save();render();}$('editor').close();};
$('input').onkeydown=e=>{if(e.key==='Enter' && !e.shiftKey){e.preventDefault();$('composer').requestSubmit();}};
$('composer').onsubmit=async e=>{e.preventDefault();const text=$('input').value.trim();if(!text || busy)return;busy=true;$('send').disabled=true;$('input').value='';state.messages.push({role:'user',content:text});save();render();const wait=make('div','loading','Thinking…');$('chat').append(wait);$('chat').scrollTop=$('chat').scrollHeight;
try{const messages=state.messages.filter(m=>state.mode || !m.edited).map(({role,content})=>({role,content}));const data={content:await providerChat(messages)};state.messages.push({role:'assistant',content:data.content});save();render();}catch(err){wait.className='error';wait.textContent='Could not send: '+err.message;}finally{busy=false;$('send').disabled=false;}};
