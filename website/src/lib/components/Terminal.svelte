<script>
	import { prefersReducedMotion, onceVisible } from '$lib/motion.js';

	/**
	 * A run of `pruefstein-agent run`, played back rather than screenshotted so
	 * it stays legible, selectable and searchable. Line shapes and wording match
	 * the agent's own output (ComplianceRunner / ConsoleStyle).
	 *
	 * Every line is in the DOM from the first paint; playback only toggles
	 * `visibility`, so the box never changes height and the prerendered HTML
	 * carries the whole transcript for anyone without JavaScript.
	 */
	let { title = 'pruefstein-agent', lines = [] } = $props();

	let host = $state(null);

	/** Index of the last revealed line. Starts finished; the effect rewinds it. */
	let step = $state(lines.length - 1);
	/** How much of the command line has been "typed". Infinity = all of it. */
	let typed = $state(Infinity);
	let done = $state(true);
	let playing = $state(false);

	/** Pause after a line of each kind, in ms. Failures get a beat to land. */
	const BEAT = { out: 260, pass: 170, fail: 300, rule: 300, sum: 240, notice: 460, ask: 400 };
	const TYPE_SPEED = 45;

	let timers = [];

	function clearTimers() {
		for (const t of timers) clearTimeout(t);
		timers = [];
	}

	function at(ms, fn) {
		timers.push(setTimeout(fn, ms));
	}

	function play() {
		clearTimers();
		step = -1;
		typed = 0;
		done = false;
		playing = true;

		let t = 450;

		lines.forEach((line, i) => {
			if (line.k === 'cmd') {
				at(t, () => (step = i));
				const chars = line.t.length;
				for (let c = 1; c <= chars; c++) at(t + c * TYPE_SPEED, () => (typed = c));
				t += chars * TYPE_SPEED + 380;
			} else {
				at(t, () => (step = i));
				t += BEAT[line.k] ?? 200;
			}
		});

		at(t, () => {
			done = true;
			playing = false;
		});
	}

	$effect(() => {
		if (!host) return;
		// Reduced motion keeps the finished transcript that is already on screen.
		if (prefersReducedMotion()) return;
		step = -1;
		typed = 0;
		done = false;

		let launched = false;
		const start = () => {
			if (launched) return;
			launched = true;
			play();
		};

		const stop = onceVisible(host, start, 0.3);

		// Safety net: a blank terminal is worse than an unseen animation, so if
		// the observer has not fired by now, show the run regardless.
		const failsafe = setTimeout(() => {
			if (!launched) {
				launched = true;
				step = lines.length - 1;
				typed = Infinity;
				done = true;
			}
		}, 3000);

		return () => {
			stop();
			clearTimeout(failsafe);
			clearTimers();
		};
	});

	const shown = (i) => i <= step;
	const cmdText = (line) => (typed === Infinity ? line.t : line.t.slice(0, typed));
</script>

<div class="term" bind:this={host}>
	<div class="chrome">
		<span class="dots" aria-hidden="true"><i></i><i></i><i></i></span>
		<span class="title">{title}</span>
		<button
			class="replay"
			onclick={play}
			disabled={playing}
			aria-label="Replay the example run"
			title="Replay"
		>
			<span class="glyph" aria-hidden="true">↻</span>
			<span class="word">Replay</span>
		</button>
	</div>

	<pre class="body"><code>{#each lines as line, i (i)}{#if line.k === 'cmd'}<span
					class="ln"
					class:on={shown(i)}><span class="prompt">$</span><span class="cmd"
						>{' ' + cmdText(line)}</span
					>{#if shown(i) && !done && step === i}<span class="caret" aria-hidden="true">▋</span
						>{/if}</span
				>{:else if line.k === 'pass'}<span class="ln" class:on={shown(i)}>{'  '}<span
						class="tag pass">[PASS]</span
					>{' ' + line.t}</span
				>{:else if line.k === 'fail'}<span class="ln flash" class:on={shown(i)}>{'  '}<span
						class="tag fail">[FAIL]</span
					>{' ' + line.t}</span
				>{:else if line.k === 'rule'}<span class="ln" class:on={shown(i)}><span class="rule"
						>{'─'.repeat(44)}</span
					></span
				>{:else if line.k === 'sum'}<span class="ln" class:on={shown(i)}><span class="sum"
						>{line.t}</span
					></span
				>{:else if line.k === 'notice'}<span class="ln" class:on={shown(i)}><span class="notice"
						>{line.t}</span
					></span
				>{:else if line.k === 'ask'}<span class="ln" class:on={shown(i)}><span class="ask"
						>{line.t}</span
					>{#if done}<span class="caret blink" aria-hidden="true">▋</span>{/if}</span
				>{:else}<span class="ln" class:on={shown(i)}><span class="dim">{line.t}</span></span
				>{/if}{/each}</code></pre>
</div>

<style>
	.term {
		border: var(--rule) solid var(--ink);
		box-shadow: var(--shadow-lg);
		background: var(--ink);
		color: #e7e5e4;
		min-width: 0;
	}

	.chrome {
		display: flex;
		align-items: center;
		gap: 0.7rem;
		padding: 0.45rem 0.55rem 0.45rem 0.85rem;
		background: #1c1917;
		border-bottom: var(--rule) solid #44403c;
	}

	.dots {
		display: inline-flex;
		gap: 0.3rem;
	}

	.dots i {
		width: 9px;
		height: 9px;
		background: #57534e;
		display: block;
		transition: background-color 0.12s linear;
	}

	.dots i:first-child {
		background: var(--accent);
	}

	.term:hover .dots i:nth-child(2) {
		background: #a8a29e;
	}
	.term:hover .dots i:nth-child(3) {
		background: #78716c;
	}

	.title {
		font-family: var(--mono);
		font-size: 0.7rem;
		letter-spacing: 0.08em;
		color: #a8a29e;
	}

	/* --- Replay ----------------------------------------------------------- */

	.replay {
		margin-left: auto;
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		font-family: var(--sans);
		font-size: 9px;
		font-weight: 700;
		letter-spacing: 0.15em;
		text-transform: uppercase;
		color: #a8a29e;
		background: transparent;
		border: 2px solid transparent;
		padding: 0.3rem 0.45rem;
		cursor: pointer;
		transition:
			color 0.1s linear,
			border-color 0.1s linear,
			background-color 0.1s linear;
	}

	.replay:hover:not(:disabled),
	.replay:focus-visible {
		color: var(--ink);
		background: var(--accent);
		border-color: var(--accent);
	}

	.replay:active:not(:disabled) {
		transform: translate(1px, 1px);
	}

	.replay:disabled {
		opacity: 0.35;
		cursor: default;
	}

	.replay .glyph {
		font-size: 12px;
		line-height: 1;
		display: inline-block;
	}

	.replay:hover:not(:disabled) .glyph {
		animation: spin 0.5s linear;
	}

	.replay .word {
		display: none;
	}

	@media (min-width: 420px) {
		.replay .word {
			display: inline;
		}
	}

	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}

	/* --- Transcript ------------------------------------------------------- */

	.body {
		margin: 0;
		padding: 1rem 1.1rem 1.2rem;
		overflow-x: auto;
		/* Sized so a full run line fits the column without a scrollbar */
		font-size: 0.7rem;
		line-height: 1.85;
	}

	code {
		white-space: pre;
	}

	/* Lines hold their space from the first paint; playback only reveals them */
	.ln {
		display: block;
		visibility: hidden;
	}

	.ln.on {
		visibility: visible;
	}

	.prompt {
		color: var(--accent);
		font-weight: 700;
	}
	.cmd {
		color: #fafaf9;
		font-weight: 700;
	}
	.dim {
		color: #a8a29e;
	}
	.tag {
		font-weight: 700;
	}
	.pass {
		color: #34d399;
	}
	.fail {
		color: #f87171;
	}
	.rule {
		color: #57534e;
	}
	.sum {
		font-weight: 700;
		color: #fafaf9;
	}
	.notice {
		font-weight: 700;
		color: #f87171;
	}
	.ask {
		color: #fafaf9;
	}

	/* Off the two-column hero there is no column wide enough for a 63-character
	   run line, and sideways scrolling inside a page that already scrolls is the
	   worst way to spend the reader's attention. So the transcript folds, one
	   size smaller, with a hanging indent that keeps the wrapped remainder clear
	   of the [PASS]/[FAIL] column. Lines still hold their own height from the
	   first paint, so playback does not reflow anything. */
	@media (max-width: 699px) {
		.body {
			padding: 0.9rem 0.9rem 1rem;
			font-size: clamp(0.62rem, 2.7vw, 0.7rem);
		}

		code {
			white-space: pre-wrap;
			overflow-wrap: break-word;
		}

		.ln {
			padding-left: 3.5ch;
			text-indent: -3.5ch;
		}
	}

	/* A failure gets one quick flash as it lands, then sits still */
	.flash.on {
		animation: land 0.45s steps(3, end) 1;
	}

	@keyframes land {
		from {
			background: rgba(248, 113, 113, 0.3);
		}
		to {
			background: transparent;
		}
	}

	.caret {
		color: var(--accent);
	}

	.caret.blink {
		animation: blink 1.1s steps(2, start) infinite;
	}

	@keyframes blink {
		to {
			visibility: hidden;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.ln {
			visibility: visible;
		}
		.flash.on,
		.caret.blink {
			animation: none;
		}
		.replay:hover:not(:disabled) .glyph {
			animation: none;
		}
	}
</style>
