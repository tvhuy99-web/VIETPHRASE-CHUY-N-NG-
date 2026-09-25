package com.oai.geminilivetranslate.ui


object AiStudioWebSessionR11Support {
    const val VERSION = "2026-09-25-web-session-r11.1-verified-model-selection"

    val DOCUMENT_START: String = """
        (function() {
          'use strict';
          if (window.__AIS_R11_SUPPORT__ && window.__AIS_R11_SUPPORT__.version === '$VERSION') return;

          const models = new Map();
          const state = {
            version: '$VERSION',
            modelRevision: 0,
            requestedModel: '',
            selectedModel: '',
            observedGenerateModel: '',
            selectionPhase: 'idle',
            selectionError: '',
            selectionAttempt: 0,
            selectionStartedAt: 0,
            selectionVerifiedAt: 0,
            selectionUiText: '',
            fileChooserServed: false,
            fileName: '',
            fileMime: '',
            fileSize: -1,
            uploadStarted: 0,
            uploadCompleted: 0,
            uploadFailed: 0,
            activeUploads: 0,
            lastUpload: null
          };

          function emit(kind, payload) {
            try {
              if (window.AIStudioWebSessionLab && window.AIStudioWebSessionLab.onJsEvent) {
                window.AIStudioWebSessionLab.onJsEvent(JSON.stringify({t:Date.now(),kind:kind,payload:payload||{}}));
              }
            } catch (_) {}
          }

          function hostPath(raw) {
            try {
              const u = new URL(String(raw || ''), location.href);
              return {host:String(u.host||'').slice(0,160),path:String(u.pathname||'').slice(0,500)};
            } catch (_) { return {host:'',path:''}; }
          }

          function normalizeModel(raw) {
            return String(raw || '')
              .trim()
              .replace(/^models\//i, '')
              .replace(/[\"'`,;:)\]}]+$/g, '')
              .slice(0,120);
          }

          function rememberModel(raw, source) {
            const id = normalizeModel(raw);
            if (!/^gemini-[a-z0-9][a-z0-9._-]{2,110}$/i.test(id)) return false;
            const existing = models.get(id) || {id:id,sources:[],firstSeenAt:Date.now(),lastSeenAt:Date.now()};
            const src = String(source || 'unknown').slice(0,120);
            if (existing.sources.indexOf(src) < 0 && existing.sources.length < 12) existing.sources.push(src);
            existing.lastSeenAt = Date.now();
            const wasNew = !models.has(id);
            models.set(id, existing);
            if (wasNew) {
              state.modelRevision += 1;
              emit('R11_MODEL_DISCOVERED',{modelId:id,source:src,modelCount:models.size,revision:state.modelRevision});
            }
            return wasNew;
          }

          function rememberModels(text, source) {
            const raw = String(text || '');
            if (!raw) return 0;
            const re = /(?:models\/)?gemini-[a-z0-9][a-z0-9._-]{2,110}/ig;
            let m, added = 0, guard = 0;
            while ((m = re.exec(raw)) && guard++ < 300) if (rememberModel(m[0], source)) added += 1;
            return added;
          }

          function modelFromText(text) {
            const m = String(text || '').match(/(?:models\/)?gemini-[a-z0-9][a-z0-9._-]{2,110}/i);
            return m ? normalizeModel(m[0]) : '';
          }

          function scanDomModels() {
            let visited = 0, added = 0;
            try {
              const nodes = document.querySelectorAll('mat-option,[role="option"],[role="menuitem"],mat-select,[role="combobox"],button,[role="button"],[data-value],[aria-label],[title]');
              for (let i=0;i<nodes.length && visited<1800;i++,visited++) {
                const el = nodes[i];
                let text = '';
                try {
                  text = [
                    el.textContent || '',
                    el.getAttribute && el.getAttribute('aria-label') || '',
                    el.getAttribute && el.getAttribute('title') || '',
                    el.getAttribute && el.getAttribute('data-value') || '',
                    el.getAttribute && el.getAttribute('value') || ''
                  ].join(' ').slice(0,1000);
                } catch (_) {}
                added += rememberModels(text,'dom');
              }
            } catch (_) {}
            return {visited:visited,added:added};
          }

          function visible(el) {
            try {
              if (!el || !el.isConnected) return false;
              const r = el.getBoundingClientRect();
              const s = getComputedStyle(el);
              return r.width >= 1 && r.height >= 1 && s.display !== 'none' && s.visibility !== 'hidden';
            } catch (_) { return false; }
          }

          function textOf(el) {
            try {
              return [
                el.textContent || '',
                el.getAttribute && el.getAttribute('aria-label') || '',
                el.getAttribute && el.getAttribute('title') || '',
                el.getAttribute && el.getAttribute('data-value') || '',
                el.getAttribute && el.getAttribute('value') || ''
              ].join(' ').replace(/\s+/g,' ').trim().slice(0,1500);
            } catch (_) { return ''; }
          }

          function clickNative(el) {
            try {
              if (window.HTMLElement && HTMLElement.prototype && HTMLElement.prototype.click) {
                HTMLElement.prototype.click.call(el);
                return true;
              }
            } catch (_) {}
            try { el.click(); return true; } catch (_) { return false; }
          }

          function comparableModelText(raw) {
            return String(raw || '')
              .toLowerCase()
              .replace(/^models\//i, '')
              .replace(/[^a-z0-9]+/g, '-')
              .replace(/^-+|-+$/g, '');
          }

          function modelTextMatchesTarget(text, target) {
            const actual = comparableModelText(text);
            const wanted = comparableModelText(target);
            if (!actual || !wanted) return false;
            if (actual === wanted || actual.indexOf(wanted) >= 0) return true;
            const tokens = wanted.split('-').filter(function(x){ return x.length > 0; });
            if (tokens.length < 3) return false;
            return tokens.every(function(token){ return actual.indexOf(token) >= 0; });
          }

          function selectedModelEvidence(target) {
            let nodes = [];
            try {
              nodes = document.querySelectorAll(
                'mat-select,[role="combobox"],button,[role="button"],[aria-selected="true"],mat-option.mat-selected,mat-option.mdc-list-item--selected'
              );
            } catch (_) {}
            let best = null, bestScore = -1, bestText = '';
            for (let i = 0; i < nodes.length && i < 2200; i++) {
              const el = nodes[i];
              if (!visible(el)) continue;
              const t = textOf(el);
              if (!modelTextMatchesTarget(t, target)) continue;
              let s = 1800;
              const role = String(el.getAttribute && el.getAttribute('role') || '').toLowerCase();
              const tag = String(el.tagName || '');
              const selected = String(el.getAttribute && el.getAttribute('aria-selected') || '').toLowerCase() === 'true';
              const expanded = String(el.getAttribute && el.getAttribute('aria-expanded') || '').toLowerCase();
              const hasPopup = String(el.getAttribute && el.getAttribute('aria-haspopup') || '').toLowerCase();
              if (tag === 'MAT-SELECT') s += 500;
              if (role === 'combobox') s += 450;
              if (selected) s += 600;
              if (expanded === 'false') s += 180;
              if (hasPopup === 'listbox' || hasPopup === 'menu') s += 180;
              if (/\bmodel\b|mô\s*hình|gemini/i.test(t)) s += 200;
              if (role === 'option' && !selected) s -= 900;
              if (role === 'menuitem' && !selected) s -= 900;
              if (s > bestScore) { best = el; bestScore = s; bestText = t; }
            }
            return {
              ok: bestScore >= 1800,
              score: bestScore,
              text: bestText.slice(0, 500),
              tag: best ? String(best.tagName || '') : '',
              role: best ? String(best.getAttribute && best.getAttribute('role') || '') : ''
            };
          }

          function currentSessionProbe() {
            const hp = hostPath(location.href);
            let controllerReady = false, candidateCount = 0;
            try {
              const d = window.__AIS_ADAPTIVE_RUNTIME__ && window.__AIS_ADAPTIVE_RUNTIME__.discover
                ? window.__AIS_ADAPTIVE_RUNTIME__.discover() : null;
              controllerReady = !!(d && d.controllerReady);
              candidateCount = Number(d && d.candidateCount || 0);
            } catch (_) {}
            let signInSurface = false, accountSurface = false;
            try {
              const sample = String(document.body && document.body.innerText || '').slice(0,120000);
              signInSurface = /\b(sign\s*in|log\s*in|đăng\s*nhập)\b/i.test(sample);
              const labelled = document.querySelectorAll('[aria-label],[title]');
              for (let i=0;i<labelled.length && i<1200;i++) {
                const t = textOf(labelled[i]);
                if (/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(t)) { accountSurface = true; break; }
              }
            } catch (_) {}
            const stateName = controllerReady ? 'AI_STUDIO_READY' : (signInSurface ? 'AUTH_REQUIRED' : 'WAITING_FOR_AI_STUDIO');
            return {
              ok:true,version:state.version,state:stateName,host:hp.host,path:hp.path,
              controllerReady:controllerReady,candidateCount:candidateCount,
              signInSurface:signInSurface,accountSurface:accountSurface,modelCount:models.size
            };
          }

          function inspectRequest(url, body, source) {
            let bodyText = '';
            try { if (typeof body === 'string') bodyText = body.slice(0,50000); } catch (_) {}
            if (bodyText) {
              rememberModels(bodyText,source+'-request');
              const hp = hostPath(url);
              if (/MakerSuiteService\/(?:GenerateContent|BidiGenerateContent)/i.test(String(url||'')) || /\/GenerateContent(?:$|[/?])/i.test(String(url||''))) {
                const model = modelFromText(bodyText);
                state.observedGenerateModel = model;
                emit('R11_GENERATE_MODEL_OBSERVED',{modelId:model,source:source,host:hp.host,path:hp.path,bodyChars:bodyText.length});
                if (model && state.requestedModel) {
                  const matches = comparableModelText(model) === comparableModelText(state.requestedModel);
                  if (matches) {
                    state.selectionPhase = 'network-verified';
                    state.selectionError = '';
                    emit('R11_MODEL_NETWORK_VERIFIED',{requestedModel:state.requestedModel,observedGenerateModel:model,source:source});
                  } else {
                    state.selectionPhase = 'network-mismatch';
                    state.selectionError = 'MODEL_NETWORK_MISMATCH';
                    emit('R11_MODEL_NETWORK_MISMATCH',{requestedModel:state.requestedModel,observedGenerateModel:model,source:source});
                  }
                }
              }
            }
          }

          function uploadBody(body) {
            try {
              if (typeof File !== 'undefined' && body instanceof File) return {hasFile:true,size:Number(body.size||-1),mime:String(body.type||'')};
              if (typeof Blob !== 'undefined' && body instanceof Blob) return {hasFile:true,size:Number(body.size||-1),mime:String(body.type||'')};
              if (typeof FormData !== 'undefined' && body instanceof FormData) {
                let size = -1, mime = '';
                for (const pair of body.entries()) {
                  const value = pair[1];
                  if ((typeof File !== 'undefined' && value instanceof File) || (typeof Blob !== 'undefined' && value instanceof Blob)) {
                    size = Number(value.size||-1); mime = String(value.type||''); return {hasFile:true,size:size,mime:mime};
                  }
                }
              }
            } catch (_) {}
            return {hasFile:false,size:-1,mime:''};
          }

          function beginUpload(url, source, meta) {
            const hp = hostPath(url);
            state.uploadStarted += 1; state.activeUploads += 1;
            state.lastUpload = {source:source,host:hp.host,path:hp.path,size:meta.size,mime:meta.mime,startedAt:Date.now(),status:-1};
            emit('R11_UPLOAD_START',{source:source,host:hp.host,path:hp.path,size:meta.size,mime:meta.mime,activeUploads:state.activeUploads,started:state.uploadStarted});
          }

          function finishUpload(url, source, ok, status) {
            const hp = hostPath(url);
            state.activeUploads = Math.max(0,state.activeUploads-1);
            if (ok) state.uploadCompleted += 1; else state.uploadFailed += 1;
            state.lastUpload = {source:source,host:hp.host,path:hp.path,status:Number(status||-1),finishedAt:Date.now(),ok:!!ok};
            emit(ok?'R11_UPLOAD_COMPLETE':'R11_UPLOAD_ERROR',{source:source,host:hp.host,path:hp.path,status:Number(status||-1),activeUploads:state.activeUploads,completed:state.uploadCompleted,failed:state.uploadFailed});
          }

          const nativeFetch = window.fetch ? window.fetch.bind(window) : null;
          if (nativeFetch) {
            window.fetch = function(input, init) {
              let url='', body=null;
              try {
                url = typeof input === 'string' ? input : (input && input.url) || '';
                body = init && Object.prototype.hasOwnProperty.call(init,'body') ? init.body : null;
                inspectRequest(url,body,'fetch');
              } catch (_) {}
              const up = uploadBody(body);
              if (up.hasFile) beginUpload(url,'fetch',up);
              const p = nativeFetch(input,init);
              p.then(function(resp){
                try {
                  const clone = resp.clone();
                  clone.text().then(function(text){
                    const added = rememberModels(String(text||'').slice(0,300000),'fetch-response');
                    if (added) emit('R11_MODEL_NETWORK_BATCH',{source:'fetch',added:added,modelCount:models.size,host:hostPath(url).host,path:hostPath(url).path});
                  }).catch(function(){});
                } catch (_) {}
                if (up.hasFile) finishUpload(url,'fetch',!!resp.ok,Number(resp.status||-1));
              }).catch(function(){ if (up.hasFile) finishUpload(url,'fetch',false,-1); });
              return p;
            };
          }

          const XHR = window.XMLHttpRequest;
          if (XHR && XHR.prototype) {
            const nativeOpen = XHR.prototype.open;
            const nativeSend = XHR.prototype.send;
            XHR.prototype.open = function(method,url) {
              this.__aisR11 = {method:String(method||'GET'),url:String(url||''),upload:null,observed:false};
              return nativeOpen.apply(this,arguments);
            };
            XHR.prototype.send = function(body) {
              const xhr = this;
              const meta = xhr.__aisR11 || {method:'POST',url:'',upload:null,observed:false};
              inspectRequest(meta.url,body,'xhr');
              const up = uploadBody(body); meta.upload = up;
              if (up.hasFile) beginUpload(meta.url,'xhr',up);
              try {
                xhr.addEventListener('readystatechange',function(){
                  if (xhr.readyState !== 4 || meta.observed) return;
                  meta.observed = true;
                  let text='';
                  try { if (!xhr.responseType || xhr.responseType === 'text') text=String(xhr.responseText||''); } catch (_) {}
                  const added = rememberModels(text.slice(0,300000),'xhr-response');
                  if (added) emit('R11_MODEL_NETWORK_BATCH',{source:'xhr',added:added,modelCount:models.size,host:hostPath(meta.url).host,path:hostPath(meta.url).path});
                },false);
                if (up.hasFile) xhr.addEventListener('loadend',function(){
                  let status=-1; try{status=Number(xhr.status||-1);}catch(_){}
                  finishUpload(meta.url,'xhr',status>=200&&status<300,status);
                },{once:true});
              } catch (_) {}
              return nativeSend.apply(this,arguments);
            };
          }

          const api = {
            version: state.version,
            probeSession: function() {
              const result = currentSessionProbe();
              emit('R11_AUTH_PROBE',result);
              return result;
            },
            discoverModels: function() {
              const dom = scanDomModels();
              const list = Array.from(models.values()).sort(function(a,b){return a.id.localeCompare(b.id);});
              const result = {ok:true,version:state.version,modelCount:list.length,revision:state.modelRevision,domVisited:dom.visited,domAdded:dom.added,models:list};
              emit('R11_MODEL_CATALOG',{modelCount:list.length,revision:state.modelRevision,domVisited:dom.visited,domAdded:dom.added,modelIds:list.slice(0,80).map(function(x){return x.id;})});
              return result;
            },
            openModelPicker: function() {
              const nodes = document.querySelectorAll('mat-select,[role="combobox"],button,[role="button"]');
              let best=null,bestScore=-1,bestText='';
              for(let i=0;i<nodes.length && i<1800;i++) {
                const el=nodes[i]; if(!visible(el)) continue;
                const t=textOf(el); let s=0;
                const role=String(el.getAttribute&&el.getAttribute('role')||'').toLowerCase();
                const popup=String(el.getAttribute&&el.getAttribute('aria-haspopup')||'').toLowerCase();
                if(/\bgemini\b/i.test(t)) s+=1100;
                if(/\bmodel\b|mô\s*hình/i.test(t)) s+=850;
                if(String(el.tagName||'')==='MAT-SELECT') s+=380;
                if(role==='combobox') s+=320;
                if(popup==='listbox'||popup==='menu') s+=180;
                if(/\brun\b|upload|attach|file|temperature|top\s*[pk]|token|safety/i.test(t)) s-=1100;
                if(s>bestScore){best=el;bestScore=s;bestText=t;}
              }
              const ok=bestScore>=850&&clickNative(best);
              emit('R11_MODEL_PICKER_OPEN',{ok:ok,score:bestScore,tag:best?String(best.tagName||''):'',role:best?String(best.getAttribute&&best.getAttribute('role')||''):'',text:bestText.slice(0,500)});
              return {ok:ok,score:bestScore,text:bestText.slice(0,500)};
            },
            selectModel: function(modelId) {
              const target=normalizeModel(modelId);
              state.requestedModel=target;
              state.selectedModel='';
              state.selectionError='';
              state.selectionAttempt+=1;
              state.selectionStartedAt=Date.now();
              state.selectionVerifiedAt=0;
              state.selectionUiText='';
              state.selectionPhase='requested';

              const already=selectedModelEvidence(target);
              if(already.ok){
                state.selectedModel=target;
                state.selectionPhase='ui-verified';
                state.selectionVerifiedAt=Date.now();
                state.selectionUiText=already.text;
                emit('R11_MODEL_UI_VERIFIED',{modelId:target,alreadySelected:true,score:already.score,tag:already.tag,role:already.role,text:already.text});
                return {ok:true,pending:false,verified:true,modelId:target,phase:state.selectionPhase};
              }

              emit('R11_MODEL_SELECT_START',{modelId:target,modelKnown:models.has(target),attempt:state.selectionAttempt});
              state.selectionPhase='picker-opening';
              const opened=this.openModelPicker();
              if(!opened.ok) {
                state.selectionPhase='failed';
                state.selectionError='MODEL_PICKER_NOT_FOUND';
                emit('R11_MODEL_SELECT_FAILED',{modelId:target,error:state.selectionError,attempt:state.selectionAttempt,pickerScore:opened.score,pickerText:opened.text||''});
                return {ok:false,error:state.selectionError,modelId:target,phase:state.selectionPhase};
              }

              state.selectionPhase='picker-opened';
              setTimeout(function(){
                let nodes=[];
                try{nodes=document.querySelectorAll('mat-option,[role="option"],[role="menuitem"],li,button[role="menuitem"],button[role="option"]');}catch(_){}
                let best=null,bestScore=-1,bestText='';
                for(let i=0;i<nodes.length && i<2600;i++) {
                  const el=nodes[i]; if(!visible(el)) continue;
                  const t=textOf(el); let s=0;
                  if(modelTextMatchesTarget(t,target)) s+=2200;
                  if(modelFromText(t)===target) s+=1200;
                  if(String(el.tagName||'')==='MAT-OPTION') s+=500;
                  const role=String(el.getAttribute&&el.getAttribute('role')||'').toLowerCase();
                  if(role==='option') s+=450;
                  if(role==='menuitem') s+=320;
                  if(/deprecated|legacy|unavailable|not available/i.test(t)) s-=1600;
                  if(s>bestScore){best=el;bestScore=s;bestText=t;}
                }
                const clicked=bestScore>=2200&&clickNative(best);
                emit('R11_MODEL_OPTION_CLICK',{ok:clicked,modelId:target,score:bestScore,tag:best?String(best.tagName||''):'',role:best?String(best.getAttribute&&best.getAttribute('role')||''):'',text:bestText.slice(0,500)});
                if(!clicked){
                  state.selectionPhase='failed';
                  state.selectionError='MODEL_OPTION_NOT_FOUND';
                  emit('R11_MODEL_SELECT_FAILED',{modelId:target,error:state.selectionError,attempt:state.selectionAttempt,optionScore:bestScore,optionText:bestText.slice(0,500)});
                  return;
                }
                state.selectionPhase='clicked';

                let verifyAttempt=0;
                const verify=function(){
                  verifyAttempt+=1;
                  const evidence=selectedModelEvidence(target);
                  if(evidence.ok){
                    state.selectedModel=target;
                    state.selectionPhase='ui-verified';
                    state.selectionError='';
                    state.selectionVerifiedAt=Date.now();
                    state.selectionUiText=evidence.text;
                    emit('R11_MODEL_UI_VERIFIED',{modelId:target,alreadySelected:false,verifyAttempt:verifyAttempt,score:evidence.score,tag:evidence.tag,role:evidence.role,text:evidence.text});
                    return;
                  }
                  if(verifyAttempt>=18){
                    state.selectionPhase='failed';
                    state.selectionError='MODEL_UI_VERIFY_FAILED';
                    emit('R11_MODEL_SELECT_FAILED',{modelId:target,error:state.selectionError,attempt:state.selectionAttempt,verifyAttempts:verifyAttempt,lastScore:evidence.score,lastText:evidence.text});
                    return;
                  }
                  if(verifyAttempt===1||verifyAttempt%5===0){
                    emit('R11_MODEL_UI_VERIFY_WAIT',{modelId:target,verifyAttempt:verifyAttempt,lastScore:evidence.score,lastText:evidence.text});
                  }
                  setTimeout(verify,200);
                };
                setTimeout(verify,220);
              },220);
              return {ok:true,pending:true,verified:false,modelId:target,phase:state.selectionPhase};
            },
            selectionState: function() {
              return {
                ok:true,
                requestedModel:state.requestedModel,
                selectedModel:state.selectedModel,
                observedGenerateModel:state.observedGenerateModel,
                phase:state.selectionPhase,
                error:state.selectionError,
                attempt:state.selectionAttempt,
                startedAgeMs:state.selectionStartedAt?Date.now()-state.selectionStartedAt:-1,
                verifiedAgeMs:state.selectionVerifiedAt?Date.now()-state.selectionVerifiedAt:-1,
                uiText:state.selectionUiText
              };
            },
            markFileChooserServed: function(name,mime,size) {
              state.fileChooserServed=true; state.fileName=String(name||'').slice(0,260); state.fileMime=String(mime||'').slice(0,180); state.fileSize=Number(size||-1);
              emit('R11_FILE_CHOOSER_SERVED',{name:state.fileName,mime:state.fileMime,size:state.fileSize});
              return {ok:true};
            },
            attachFile: function() {
              state.fileChooserServed=false; state.uploadStarted=0; state.uploadCompleted=0; state.uploadFailed=0; state.activeUploads=0; state.lastUpload=null;
              let input=null;
              try {
                const inputs=document.querySelectorAll('input[type="file"]');
                for(let i=inputs.length-1;i>=0;i--) if(inputs[i].isConnected){input=inputs[i];break;}
              } catch (_) {}
              if(input && clickNative(input)) {
                emit('R11_ATTACH_TRIGGER',{ok:true,path:'file-input',inputCount:document.querySelectorAll('input[type="file"]').length});
                return {ok:true,path:'file-input'};
              }
              let nodes=[];try{nodes=document.querySelectorAll('button,[role="button"],[aria-label],[title]');}catch(_){}
              let best=null,bestScore=-1;
              for(let i=0;i<nodes.length&&i<2200;i++){
                const el=nodes[i];if(!visible(el))continue;const t=textOf(el);let s=0;
                if(/attach|upload|add\s*file|file|đính\s*kèm|tải\s*tệp|thêm\s*tệp/i.test(t))s+=900;
                if(/video|media/i.test(t))s+=220;
                if(String(el.tagName||'')==='BUTTON')s+=120;
                if(s>bestScore){best=el;bestScore=s;}
              }
              const ok=bestScore>=900&&clickNative(best);
              emit('R11_ATTACH_TRIGGER',{ok:ok,path:'attachment-button',score:bestScore,tag:best?String(best.tagName||''):''});
              return ok?{ok:true,path:'attachment-button',pendingFileChooser:true}:{ok:false,error:'ATTACH_CONTROL_NOT_FOUND',score:bestScore};
            },
            attachmentState: function(expectedName) {
              const name=String(expectedName||state.fileName||'');let nameVisible=false,busy=false;
              try {
                const text=String(document.body&&document.body.innerText||'').slice(-180000);
                if(name) nameVisible=text.indexOf(name)>=0;
                busy=/uploading|processing|preparing|đang\s*tải|đang\s*xử\s*lý/i.test(text.slice(-30000));
              } catch (_) {}
              const ready=state.fileChooserServed&&state.activeUploads===0&&(state.uploadCompleted>0||nameVisible)&&!busy;
              return {ok:true,ready:ready,fileChooserServed:state.fileChooserServed,nameVisible:nameVisible,busy:busy,activeUploads:state.activeUploads,uploadStarted:state.uploadStarted,uploadCompleted:state.uploadCompleted,uploadFailed:state.uploadFailed,fileName:state.fileName,fileMime:state.fileMime,fileSize:state.fileSize,lastUpload:state.lastUpload};
            },
            diagnostics: function() {
              return {ok:true,version:state.version,session:currentSessionProbe(),modelCount:models.size,selection:this.selectionState(),attachment:this.attachmentState(state.fileName)};
            }
          };

          window.__AIS_R11_SUPPORT__ = api;
          emit('R11_SUPPORT_INSTALLED',{version:state.version,host:hostPath(location.href).host,path:hostPath(location.href).path});
        })();
    """.trimIndent()
}
