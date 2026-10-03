(function () {
    'use strict';

    var ACTIVE_CLASSES = ['bg-amber-100', 'border-amber-300'];
    var INACTIVE_CLASSES = ['border-zinc-200'];

    function starsFor(rating) {
        if (rating === 5) return '★★★★★';
        if (rating === 4) return '★★★★☆';
        if (rating === 3) return '★★★☆☆';
        if (rating === 2) return '★★☆☆☆';
        if (rating === 1) return '★☆☆☆☆';
        return '';
    }

    function getGrid() {
        return document.getElementById('library-grid');
    }

    function getCount() {
        return document.getElementById('library-count');
    }

    function decrementCount() {
        var el = getCount();
        if (!el) return;
        var match = /(\d+)/.exec(el.textContent || '');
        if (!match) return;
        var next = Math.max(0, parseInt(match[1], 10) - 1);
        el.textContent = next + ' items';
    }

    function emptyHtml(page) {
        if (page === 'watched') {
            return '<div class="bg-white border border-zinc-200 rounded-sm shadow-sm p-8 text-center">'
                + '<p class="text-sm text-zinc-600">Your Watched list is empty — mark items as watched with a rating from <a href="/" class="underline">/</a> or detail pages.</p>'
                + '</div>';
        }
        return '<div class="bg-white border border-zinc-200 rounded-sm shadow-sm p-8 text-center">'
            + '<p class="text-sm text-zinc-600">Your Watchlist is empty — search above in <a href="/" class="underline">/</a>.</p>'
            + '</div>';
    }

    function showEmptyIfNeeded() {
        var grid = getGrid();
        if (!grid) return;
        if (grid.querySelector('[data-card]')) return;
        var wrapper = grid.parentElement;
        if (!wrapper) return;
        grid.remove();
        var empty = document.createElement('div');
        empty.setAttribute('id', 'library-empty');
        empty.innerHTML = emptyHtml(grid.getAttribute('data-page'));
        wrapper.appendChild(empty);
    }

    function removeCard(btn) {
        var card = btn.closest('[data-card]');
        if (card) card.remove();
        decrementCount();
        showEmptyIfNeeded();
    }

    function setRateButtonState(btn, active) {
        ACTIVE_CLASSES.forEach(function (c) {
            if (active) btn.classList.add(c);
            else btn.classList.remove(c);
        });
        INACTIVE_CLASSES.forEach(function (c) {
            if (active) btn.classList.remove(c);
            else btn.classList.add(c);
        });
    }

    function updateRating(container, rating) {
        container.setAttribute('data-current-rating', String(rating));
        var display = container.parentElement
            ? container.parentElement.querySelector('[data-stars-display]')
            : null;
        if (display) {
            display.innerHTML = starsFor(rating)
                + ' <span class="text-xs font-mono text-zinc-500">(' + rating + '/5)</span>';
        }
        container.querySelectorAll('.rate-btn[data-rate]').forEach(function (b) {
            var btnRate = Number(b.getAttribute('data-rate'));
            setRateButtonState(b, btnRate === rating);
            b.disabled = false;
        });
    }

    function setRateGroupDisabled(container, disabled) {
        container.querySelectorAll('.rate-btn').forEach(function (b) {
            b.disabled = disabled;
        });
    }

    function readError(res, fallback) {
        return res.json().then(function (j) {
            throw new Error((j && j.error) || fallback);
        }).catch(function (e) {
            if (e instanceof Error) throw e;
            throw new Error(fallback);
        });
    }

    function onRemoveClick(event) {
        var btn = event.currentTarget;
        var message = btn.getAttribute('data-confirm') || 'Remove this item?';
        if (!window.confirm(message)) return;
        var id = btn.getAttribute('data-entry-id');
        if (!id) return;
        btn.disabled = true;
        fetch('/api/me/library/' + id, { method: 'DELETE', credentials: 'include' })
            .then(function (r) {
                if (r.status === 204) {
                    removeCard(btn);
                    return null;
                }
                return readError(r, 'Failed');
            })
            .catch(function (e) {
                window.alert((e && e.message) || 'Failed');
                btn.disabled = false;
            });
    }

    function onRateClick(event) {
        var btn = event.currentTarget;
        var container = btn.closest('[data-entry-id]');
        if (!container) return;
        var id = container.getAttribute('data-entry-id');
        var rating = Number(btn.getAttribute('data-rate'));
        if (!id || !rating) return;
        setRateGroupDisabled(container, true);
        fetch('/api/me/library/' + id, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ rating: rating })
        })
            .then(function (r) {
                if (r.ok) {
                    updateRating(container, rating);
                    return null;
                }
                return readError(r, 'Failed');
            })
            .catch(function (e) {
                window.alert((e && e.message) || 'Failed');
                setRateGroupDisabled(container, false);
            });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.remove-btn[data-entry-id]').forEach(function (btn) {
            btn.addEventListener('click', onRemoveClick);
        });
        document.querySelectorAll('.rate-btn[data-rate]').forEach(function (btn) {
            btn.addEventListener('click', onRateClick);
        });
    });
})();
