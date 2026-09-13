(function () {
    const trigger = document.getElementById('chatbot-trigger');
    const panel = document.getElementById('chatbot-panel');
    const overlay = document.getElementById('chatbot-overlay');
    const closeBtn = document.getElementById('chatbot-close');
    const messagesEl = document.getElementById('chatbot-messages');
    const form = document.getElementById('chatbot-form');
    const input = document.getElementById('chatbot-input');
    const loadingEl = document.getElementById('chatbot-loading');
    const charCount = document.getElementById('chatbot-char-count');
    const wsDot = document.getElementById('chatbot-ws-dot');
    const wsLabel = document.getElementById('chatbot-ws-label');
    const attachBtn = document.getElementById('chatbot-attach');
    const fileInput = document.getElementById('chatbot-file');
    const previewEl = document.getElementById('chatbot-preview');
    const previewImg = document.getElementById('chatbot-preview-img');
    const previewName = document.getElementById('chatbot-preview-name');
    const previewRemove = document.getElementById('chatbot-preview-remove');

    const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
    let selectedFile = null;
    let previewObjectUrl = null;

    if (!trigger || !panel || !messagesEl || !form || !input) return;

    let ws = null;
    let wsState = 'CONNECTING';
    let isOpen = false;
    let isLoading = false;
    let streamingRow = null;
    let streamingContentEl = null;
    let streamingText = "";
    let streamingCopyBtn = null;

    function setWsState(state) {
        wsState = state;
        if (wsDot) wsDot.className = 'h-2 w-2 rounded-full ' + (state === 'OPEN' ? 'bg-emerald-500' : state === 'CONNECTING' ? 'bg-amber-400' : 'bg-zinc-400');
        if (wsLabel) {
            wsLabel.textContent = state === 'OPEN' ? 'connected' : state === 'CONNECTING' ? 'connecting…' : 'disconnected';
            wsLabel.className = state === 'OPEN' ? 'text-emerald-600' : 'text-zinc-500';
        }
    }

    function createStreamingRow() {
        const row = document.createElement('div');
        row.className = 'border border-zinc-200 rounded-sm p-3 bg-zinc-50';
        row.innerHTML = '<div class="flex items-center gap-2 text-xs font-mono text-zinc-500"><span class="h-6 w-6 rounded-sm bg-zinc-900 text-white grid place-items-center text-[11px]">AI</span> Assistant <span class="text-zinc-400">via ws</span><span class="ml-auto flex items-center gap-1 text-[11px] font-mono text-zinc-400"><span class="h-1.5 w-1.5 bg-emerald-500 rounded-full animate-pulse"></span> streaming…</span><button type="button" class="chatbot-copy ml-auto hidden text-[11px] border border-zinc-200 bg-white hover:bg-zinc-50 rounded-sm px-2 py-0.5">Copy</button></div><div class="mt-2 text-sm leading-relaxed text-zinc-700 chatbot-markdown break-words"></div>';
        messagesEl.appendChild(row);
        scrollToBottom();
        return row;
    }

    function connect() {
        setWsState('CONNECTING');
        const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
        const url = proto + location.host + '/chatbot';
        try {
            ws = new WebSocket(url);
        } catch (e) {
            setWsState('CLOSED');
            setTimeout(connect, 3000);
            return;
        }
        ws.onopen = function () {
            setWsState('OPEN');
        };
        ws.onclose = function () {
            setWsState('CLOSED');
            if (isLoading) setLoading(false);
            setTimeout(connect, 3000);
        };
        ws.onerror = function () {
            setWsState('CLOSED');
            if (isLoading) setLoading(false);
        };
        ws.onmessage = function (event) {
            const token = event.data;
            if (streamingRow === null) {
                setLoading(false);
                streamingRow = createStreamingRow();
                streamingContentEl = streamingRow.querySelector('.chatbot-markdown');
                streamingCopyBtn = streamingRow.querySelector('.chatbot-copy');
                const streamingIndicator = streamingRow.querySelector('.animate-pulse')?.parentElement;
                if (streamingIndicator) streamingIndicator.classList.add('hidden');
                if (streamingCopyBtn) streamingCopyBtn.classList.remove('hidden');
                streamingText = "";
                if (streamingCopyBtn) {
                    streamingCopyBtn.addEventListener('click', function () {
                        navigator.clipboard.writeText(streamingText).then(function () {
                            streamingCopyBtn.textContent = 'Copied';
                            setTimeout(function () { streamingCopyBtn.textContent = 'Copy'; }, 1200);
                        });
                    });
                }
            }
            streamingText += token;
            if (streamingContentEl) {
                streamingContentEl.innerHTML = renderMarkdown(streamingText);
        scrollToBottom();
    }

    function addGuessToWishlist(data, btn) {
        const mediaType = data.catalogMediaType === 'TV_SERIES' ? 'tv' : 'movie';
        btn.disabled = true;
        const detailUrl = data.detailUrl;
        const ensureDetail = detailUrl
            ? fetch(detailUrl, { credentials: 'include' }).then(function (res) { return res.json(); })
            : Promise.resolve(null);
        ensureDetail.then(function () {
            return fetch('/api/me/library', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ externalId: data.catalogId, mediaType: mediaType })
            });
        }).then(function (res) {
            if (res.status === 409) throw new Error('Already in your library.');
            if (!res.ok) throw new Error('Could not add — please try again.');
            return res.json();
        }).then(function () {
            appendAssistant('Added ' + data.guessTitle + ' to your wishlist.');
            btn.textContent = 'Added ✓';
        }).catch(function (err) {
            appendAssistant(err && err.message ? err.message : 'Could not add — please try again.');
            btn.disabled = false;
        });
    }
        };
    }

    function setLoading(on) {
        isLoading = on;
        if (loadingEl) loadingEl.classList.toggle('hidden', !on);
        if (input) input.disabled = on;
        const submit = form.querySelector('button[type="submit"]');
        if (submit) submit.disabled = on;
        if (on && input) input.focus();
    }

    function escapeHtml(s) {
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function renderMarkdown(md) {
        const raw = String(md || '');
        if (window.marked) {
            try {
                if (window.marked.setOptions) window.marked.setOptions({ gfm: true, breaks: true });
                const html = window.marked.parse(raw);
                if (window.DOMPurify) return window.DOMPurify.sanitize(html);
                return html;
            } catch (e) {
                // fallback
            }
        }
        // fallback minimal markdown
        let out = escapeHtml(raw);
        out = out.replace(/^### (.+)$/gm, '<h3>$1</h3>')
                 .replace(/^## (.+)$/gm, '<h2>$1</h2>')
                 .replace(/^# (.+)$/gm, '<h1>$1</h1>')
                 .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
                 .replace(/\*(.+?)\*/g, '<em>$1</em>')
                 .replace(/`([^`]+)`/g, '<code>$1</code>')
                 .replace(/\n/g, '<br>');
        return out;
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function appendUser(text) {
        const row = document.createElement('div');
        row.className = 'flex justify-end';
        row.innerHTML = '<div class="max-w-[75%] bg-zinc-900 text-white rounded-sm px-3 py-2 text-sm leading-relaxed">' + escapeHtml(text) + '<div class="text-[11px] font-mono text-zinc-400 mt-1 text-right">you</div></div>';
        messagesEl.appendChild(row);
        scrollToBottom();
    }

    function appendAssistant(text) {
        const row = document.createElement('div');
        row.className = 'border border-zinc-200 rounded-sm p-3 bg-zinc-50';
        const html = renderMarkdown(text);
        row.innerHTML = '<div class="flex items-center gap-2 text-xs font-mono text-zinc-500"><span class="h-6 w-6 rounded-sm bg-zinc-900 text-white grid place-items-center text-[11px]">AI</span> Assistant <span class="text-zinc-400">via ws</span><button type="button" class="chatbot-copy ml-auto text-[11px] border border-zinc-200 bg-white hover:bg-zinc-50 rounded-sm px-2 py-0.5">Copy</button></div><div class="mt-2 text-sm leading-relaxed text-zinc-700 chatbot-markdown break-words">' + html + '</div>';
        const copyBtn = row.querySelector('.chatbot-copy');
        if (copyBtn) {
            copyBtn.addEventListener('click', function () {
                navigator.clipboard.writeText(text).then(function () {
                    copyBtn.textContent = 'Copied';
                    setTimeout(function () { copyBtn.textContent = 'Copy'; }, 1200);
                });
            });
        }
        messagesEl.appendChild(row);
        scrollToBottom();
    }

    function clearSelectedImage() {
        selectedFile = null;
        if (previewObjectUrl) {
            URL.revokeObjectURL(previewObjectUrl);
            previewObjectUrl = null;
        }
        if (fileInput) fileInput.value = '';
        if (previewEl) {
            previewEl.classList.add('hidden');
            previewEl.classList.remove('flex');
        }
        if (previewImg) previewImg.src = '';
        if (previewName) previewName.textContent = '';
    }

    function showSelectedImage(file) {
        clearSelectedImage();
        selectedFile = file;
        previewObjectUrl = URL.createObjectURL(file);
        if (previewImg) previewImg.src = previewObjectUrl;
        if (previewName) previewName.textContent = file.name + ' (' + Math.round(file.size / 1024) + ' KB)';
        if (previewEl) {
            previewEl.classList.remove('hidden');
            previewEl.classList.add('flex');
        }
        input.focus();
    }

    function appendUserImage(objectUrl, caption) {
        const row = document.createElement('div');
        row.className = 'flex justify-end';
        let inner = '<div class="max-w-[75%] bg-zinc-900 text-white rounded-sm px-3 py-2 text-sm leading-relaxed">';
        inner += '<img src="' + objectUrl + '" alt="Uploaded image" class="mb-2 max-h-40 w-auto rounded-sm border border-zinc-700" />';
        if (caption) inner += '<div>' + escapeHtml(caption) + '</div>';
        inner += '<div class="text-[11px] font-mono text-zinc-400 mt-1 text-right">you · image</div></div>';
        row.innerHTML = inner;
        messagesEl.appendChild(row);
        scrollToBottom();
    }

    function appendGuessCard(data) {
        const row = document.createElement('div');
        row.className = 'border border-zinc-200 rounded-sm p-3 bg-zinc-50';
        let html = '<div class="flex items-center gap-2 text-xs font-mono text-zinc-500"><span class="h-6 w-6 rounded-sm bg-zinc-900 text-white grid place-items-center text-[11px]">AI</span> Image Guess</div>';
        html += '<div class="mt-2 text-sm leading-relaxed text-zinc-700 break-words">' + escapeHtml(data.message || '') + '</div>';
        if (data && data.guessTitle && data.guessTitle !== 'UNKNOWN') {
            html += '<div class="mt-2 flex items-center gap-3 border border-zinc-200 bg-white rounded-sm p-2">';
            if (data.posterUrl) html += '<img src="' + escapeHtml(data.posterUrl) + '" alt="Poster" class="h-16 w-11 object-cover border border-zinc-200 rounded-sm flex-shrink-0" />';
            html += '<div class="min-w-0"><div class="text-sm font-medium truncate">' + escapeHtml(data.guessTitle) + '</div>';
            html += '<div class="text-xs font-mono text-zinc-500">' + escapeHtml(data.mediaType || '') + (data.year && data.year !== '?' ? ' · ' + escapeHtml(data.year) : '') + ' · ' + escapeHtml(data.confidence || '') + '</div>';
            if (data.detailUrl) html += '<button type="button" data-guess-detail="' + escapeHtml(data.detailUrl) + '" class="mt-1 text-xs border border-zinc-200 bg-white hover:bg-zinc-50 rounded-sm px-2 py-1">View details →</button>';
            if (data.suggestAdd && data.catalogId) html += '<button type="button" data-guess-add class="mt-1 ml-1 text-xs border border-zinc-900 bg-zinc-900 text-white hover:bg-zinc-700 rounded-sm px-2 py-1">Add to wishlist +</button>';
            html += '</div></div>';
        }
        row.innerHTML = html;
        messagesEl.appendChild(row);
        const detailBtn = row.querySelector('[data-guess-detail]');
        if (detailBtn) {
            detailBtn.addEventListener('click', function () {
                const url = detailBtn.getAttribute('data-guess-detail');
                fetch(url, { credentials: 'include' })
                    .then(function (res) { return res.json(); })
                    .then(function (detail) {
                        if (detail && detail.mediaItem) appendAssistant('Found in catalog: ' + detail.mediaItem.title + (detail.posterUrl ? '' : ''));
                        else appendAssistant('Catalog entry not available for this guess yet.');
                    })
                    .catch(function () { appendAssistant('Catalog entry not available for this guess yet.'); });
            });
        }
        const addBtn = row.querySelector('[data-guess-add]');
        if (addBtn) {
            addBtn.addEventListener('click', function () { addGuessToWishlist(data, addBtn); });
        }
        scrollToBottom();
    }

    function downscaleImage(file) {
        return new Promise(function (resolve) {
            const url = URL.createObjectURL(file);
            const img = new Image();
            img.onload = function () {
                URL.revokeObjectURL(url);
                const maxEdge = 1024;
                const longest = Math.max(img.naturalWidth, img.naturalHeight);
                if (!longest || longest <= maxEdge) {
                    resolve(file);
                    return;
                }
                const scale = maxEdge / longest;
                const w = Math.round(img.naturalWidth * scale);
                const h = Math.round(img.naturalHeight * scale);
                const canvas = document.createElement('canvas');
                canvas.width = w;
                canvas.height = h;
                canvas.getContext('2d').drawImage(img, 0, 0, w, h);
                const outType = file.type === 'image/png' || file.type === 'image/webp' ? file.type : 'image/jpeg';
                canvas.toBlob(function (blob) {
                    if (blob) resolve(new File([blob], file.name, { type: blob.type || outType }));
                    else resolve(file);
                }, outType, 0.85);
            };
            img.onerror = function () {
                URL.revokeObjectURL(url);
                resolve(file);
            };
            img.src = url;
        });
    }

    function uploadImage(file, caption) {
        const captionText = (caption || '').trim();
        const userPreviewUrl = URL.createObjectURL(file);
        appendUserImage(userPreviewUrl, captionText);
        setLoading(true);
        downscaleImage(file).then(function (payload) {
            const form = new FormData();
            form.append('image', payload, payload.name || 'upload');
            if (captionText) form.append('prompt', captionText);
            return fetch('/api/ai/guess-image', { method: 'POST', body: form, credentials: 'include' });
        }).then(function (res) {
            if (res.status === 413) throw new Error('Image is too large — please use a file under 5MB.');
            if (res.status === 415) throw new Error('Unsupported image type — please use jpeg, png, or webp.');
            if (!res.ok) throw new Error('Upload failed — please try again.');
            return res.json();
        }).then(function (data) {
            appendGuessCard(data);
        }).catch(function (err) {
            appendAssistant(err && err.message ? err.message : 'Upload failed — please try again.');
        }).finally(function () {
            setLoading(false);
            clearSelectedImage();
            input.value = '';
            if (charCount) charCount.textContent = '0';
            input.style.height = 'auto';
        });
    }

    function openPanel() {
        isOpen = true;
        panel.classList.remove('hidden');
        panel.classList.add('flex');
        overlay.classList.remove('hidden');
        trigger.classList.add('hidden');
        setTimeout(function () { input.focus(); scrollToBottom(); }, 50);
    }

    function closePanel() {
        isOpen = false;
        panel.classList.add('hidden');
        panel.classList.remove('flex');
        overlay.classList.add('hidden');
        trigger.classList.remove('hidden');
    }

    function sendMessage(text) {
        const t = (text || '').trim();
        if (isLoading) return;
        if (selectedFile) {
            uploadImage(selectedFile, t);
            return;
        }
        if (!t) return;
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            appendAssistant('Connecting… please try again in a moment.');
            if (!ws || ws.readyState === WebSocket.CLOSED) connect();
            return;
        }
        if (isLoading) return;
        appendUser(t);
        // reset streaming state for new assistant response
        streamingRow = null;
        streamingContentEl = null;
        streamingText = "";
        streamingCopyBtn = null;
        setLoading(true);
        input.value = '';
        if (charCount) charCount.textContent = '0';
        input.style.height = 'auto';
        ws.send(t);
    }

    const clearBtn = document.getElementById('chatbot-clear');
    const initialWelcomeHtml = messagesEl.innerHTML;

    function clearMessages() {
        messagesEl.innerHTML = initialWelcomeHtml;
        clearSelectedImage();
        streamingRow = null;
        streamingContentEl = null;
        streamingText = "";
        streamingCopyBtn = null;
        // re-bind quick buttons after reset (they are inside messagesEl)
        messagesEl.querySelectorAll('[data-quick]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const msg = btn.getAttribute('data-quick');
                if (!isOpen) openPanel();
                sendMessage(msg);
            });
        });
        scrollToBottom();
    }

    trigger.addEventListener('click', openPanel);
    if (closeBtn) closeBtn.addEventListener('click', closePanel);
    if (overlay) overlay.addEventListener('click', closePanel);
    if (clearBtn) clearBtn.addEventListener('click', clearMessages);
    if (attachBtn && fileInput) {
        attachBtn.addEventListener('click', function () { fileInput.click(); });
        fileInput.addEventListener('change', function () {
            const file = fileInput.files && fileInput.files[0];
            if (!file) return;
            if (ALLOWED_IMAGE_TYPES.indexOf(file.type) < 0) {
                appendAssistant('Unsupported image type — please use jpeg, png, or webp.');
                fileInput.value = '';
                return;
            }
            if (file.size > MAX_IMAGE_BYTES) {
                appendAssistant('Image is too large — please use a file under 5MB.');
                fileInput.value = '';
                return;
            }
            if (!isOpen) openPanel();
            showSelectedImage(file);
        });
    }
    if (previewRemove) previewRemove.addEventListener('click', clearSelectedImage);

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        sendMessage(input.value);
    });

    input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage(input.value);
        }
    });

    input.addEventListener('input', function () {
        if (charCount) charCount.textContent = String(input.value.length);
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 112) + 'px';
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && isOpen) closePanel();
    });

    document.querySelectorAll('[data-quick]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const msg = btn.getAttribute('data-quick');
            if (!isOpen) openPanel();
            sendMessage(msg);
        });
    });

    connect();
})();
