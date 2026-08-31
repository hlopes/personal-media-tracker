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
        const t = text.trim();
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
