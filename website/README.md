# pruefstein.com

The public site for Prüfstein. SvelteKit with `adapter-static`, prerendered to
plain files — there is no server to run.

## Commands

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # writes build/
npm run preview  # serve build/ locally
```

`npm run build` produces a `build/` directory of static HTML, CSS, JS and
assets. Point any static host (Netlify, Cloudflare Pages, GitHub Pages, nginx)
at it. `fallback: '404.html'` in `svelte.config.js` gives hosts that want one a
404 page; nothing else is required.

## Deployment

`.github/workflows/website.yml` builds this directory and publishes it to
GitHub Pages on every push to `main` that touches `website/`. Pull requests
build and run the checks but do not deploy. It is a separate workflow from
`ci.yml` on purpose: a copy change should not wait on a GraalVM native build,
or fail because of one.

The site is currently served from the default project URL,
<https://explore-de.github.io/pruefstein/>, which is a **sub-path**. That is why
the workflow builds with `BASE_PATH=/<repo>`, read by `paths.base` in
`svelte.config.js`. SvelteKit then emits every asset and internal link
relative (`./_app/…`, `../_app/…`), which works at any prefix. Two checks in the
workflow guard it, because a root-absolute asset URL would silently 404 and
leave an unstyled page behind a green build.

Anything referencing a file in `static/` by an absolute path has to go through
`base` from `$app/paths` (`Stone.svelte` and the footer's Impressum link do).
Fonts are the exception: they are referenced from `app.css`, which Vite rewrites
to a relative URL at build time on its own.

`static/.nojekyll` stays regardless. Jekyll drops every directory beginning with
an underscore, which is exactly where SvelteKit puts its JS and CSS.
Actions-based deployment does not run Jekyll, but this costs nothing and
survives a change of deployment method.

`static/_headers` is Netlify/Cloudflare syntax and is inert on Pages, which sets
its own cache headers. It is kept for portability, not because Pages uses it.

### Moving to pruefstein.com later

Three edits, no restructuring:

1. `domain` and `url` in `src/lib/data/site.js` (and `static/sitemap.xml`,
   `static/robots.txt`, which are plain files).
2. Delete the `BASE_PATH` env from the build step in the workflow.
3. Re-add `static/CNAME` containing `pruefstein.com`, then point the apex `A`
   records at GitHub Pages (`185.199.108-111.153`) and **remove the existing
   `AAAA` record**, which currently sends IPv6 clients to Hetzner.

## Layout

```
src/
  app.css                  design tokens — the app's palette, type and rules
  app.html                 document shell
  lib/
    motion.js              reduced-motion check + "run once when visible"
    actions/reveal.js      scroll-reveal action and the nav scroll-spy
    components/            primitives first, then one component per section
      Button  Card  Badge  CodeBlock  Section  Stone  Terminal
      LogoMark  ExpLogo
      Nav  Hero  HowItWorks  Trust  CheckAnatomy  Features  Catalog
      AiAssist  OpenSource  Contribute  Footer
    data/
      site.js              name, URLs, nav, and the EXP company particulars
      features.js          the feature grid
      steps.js             the four "how it works" steps
      checks.js            example checks + the seeded catalog
      trust.js             the consent/trust position
      ai.js                what the AI does, create and fix
      contribute.js        contribution lanes and open-source promises
  routes/
    +layout.svelte         nav + footer
    +layout.js             prerender = true
    +page.svelte           the page, plus <head> metadata and JSON-LD
    +error.svelte          404
    impressum/+page.svelte § 5 TMG imprint
static/
  art/stone-{1..4}.{jpg,webp}   the app's artwork, resized to 1200px
  fonts/                        Space Grotesk + Space Mono, self-hosted
  logo.svg  exp-logo.svg  og.png  robots.txt  sitemap.xml  _headers
```

## Where the design comes from

Nothing here was invented. `src/app.css` is the application shell's palette and
structure lifted out of `web/src/main/resources/templates/main.html`:

| Token             | Value                     | In the app                        |
| ----------------- | ------------------------- | --------------------------------- |
| `--paper`         | `#f5f5f4`                 | `bg-stone-100` on `<body>`        |
| `--accent`        | `#facc15`                 | Tailwind `colors.accent`          |
| `--ok` / `--bad`  | `#34d399` / `#f87171`     | dashboard compliant / non-compliant tiles |
| `--shadow`        | `4px 4px 0 0 #000`        | `shadow-brutal`                   |
| `--rule`          | `2px`                     | `border-2 border-black`           |
| `--sans`          | Space Grotesk             | the app's Google Font             |

Hover behaviour matches too: a card presses into its own shadow rather than
lifting. No border radii anywhere, because the app has none.

## Content accuracy

The example checks in `src/lib/data/checks.js` and the catalog listing are
copied verbatim from
`web/src/main/java/com/pruefstein/compliance/bootstrap/ComplianceCatalog.java`,
and the terminal transcript in `Hero.svelte` follows the agent's real output
(`agent/src/main/java/com/pruefstein/agent/runner/`). If either changes, change
them here as well — the point of showing real SQL is that it is real.

`src/lib/data/trust.js` makes claims about behaviour, not about intentions:
each line corresponds to something in `Prompt`/`RunCommand` (an explicit "y",
nothing resident), the report RBAC (own reports only), or the deadline job (a
`MISSING` report filed automatically). `src/lib/data/ai.js` follows the shipped
system prompts in `web/src/main/resources/prompts/`. The "bring your own model"
configuration is the `quarkus-langchain4j-openai` extension's own properties —
key, model name and base URL — so any OpenAI-compatible endpoint works without
a code change.

## Responsive

Every layout is mobile-first: one column by default, `@media (min-width: …)`
adding the second and third. There is no `max-width` breakpoint anywhere except
two places that deliberately fold something on a phone, both at `699px` — one
below the narrowest layout breakpoint any component uses.

Two rules keep it honest:

- **Nothing scrolls sideways except the page's one deliberately wide image.**
  A code plate or a terminal transcript that scrolls horizontally inside a page
  that scrolls vertically just hides its own second half, so below `699px`
  `CodeBlock` and `Terminal` fold their content instead (`Terminal` with a
  hanging indent, so a wrapped line stays clear of the `[PASS]`/`[FAIL]`
  column). The one exception is the wide reports screenshot in `Screenshots`,
  which is 4:1 and would be a texture rather than a picture if it were squeezed
  to fit; its frame scrolls, and its scrollbar says so.

- **Every grid item that can hold something wide carries `min-width: 0`**, and
  every `1fr` track that can is written `minmax(0, 1fr)`. A grid item's
  automatic minimum size is its min-content width, and `overflow-x: auto` on a
  descendant does not stop that contribution from propagating — so one long
  `brew install` line or one `min-width: 680px` image will otherwise set the
  width of the whole document and drag everything else off the right edge. It
  fails silently on a desktop, which is exactly why it is worth naming.

The cheapest check: load the page at 390px and compare
`document.documentElement.scrollWidth` against `clientWidth`. They must be
equal.

## Motion

Everything that moves is decoration over content that is already correct
without it, and the rules follow from that:

- **`prefers-reduced-motion` jumps to the finished state**, never to a shorter
  animation. `src/lib/motion.js` is the single place that asks.
- **Nothing animates in from nothing.** Components render finished on the
  server; the client rewinds them only after it has taken responsibility for
  playing them back. The prerendered HTML therefore carries the full terminal
  transcript, `19` in the catalog heading, and no hidden sections — so the page
  reads correctly with JavaScript off.
- **The terminal reserves its height from the first paint.** Playback toggles
  `visibility` on lines that are already in the DOM, so nothing reflows and
  nothing below it jumps.
- **Animation starts when it is on screen**, via `onceVisible`, and is not
  repeated on the way back up.

The pieces: a typed-out agent run with a Replay button (`Terminal.svelte`), a
copy button on every code plate, nav links that light up for the section being
read (mirroring the app sidebar's `nav-link-active`), sections that settle in
on scroll, a count-up on the catalog heading, parallax on the monoliths,
keyboard-navigable tabs in the check anatomy, inverting trust tiles, and
buttons that press all the way into their shadow on click.

The clipboard write races the async API against a 700 ms timeout and falls back
to the legacy selection trick, because `navigator.clipboard.writeText` can
reject *or* simply never settle when the document is not focused — the button
must always end up saying something true.

## Publisher and imprint

Prüfstein is built by **EXP Software GmbH** (explore.de). The company details in
`src/lib/data/site.js` under `company` are the § 5 TMG mandatory particulars as
published at <https://explore.de/impressum> — company name, address, contact,
managing director, register court and number, VAT ID. Change them there and both
the footer and `/impressum/` follow.

`/impressum/` is `noindex, follow` and deliberately kept out of `sitemap.xml`;
it is linked from the footer of every page, which is what "ständig verfügbar"
requires. It does not reproduce EXP's liability and copyright boilerplate —
it links to the full imprint on explore.de instead.

**Datenschutz points at explore.de.** The site itself sets no cookies, loads
nothing from a third party and runs no analytics, so there is little to
disclose beyond whatever the host logs — but a lawyer should decide whether
pruefstein.com needs its own Datenschutzerklärung rather than borrowing EXP's.

The EXP wordmark is inlined as `ExpLogo.svelte` (paths unaltered from
`explore.de/img/exp-logo-orange.svg`, brand orange `#ff8200`) so the footer
makes no request to a third-party origin.

## Fonts

Space Grotesk and Space Mono are checked in under `static/fonts/` rather than
loaded from Google. The site therefore makes no third-party requests and sets
no cookies, which is worth keeping for a German ISO 27001 product. Both are
under the SIL Open Font License.

## The mark

A faceted stone: a touchstone, which is what *Prüfstein* means. It lives in two
places, byte-identical, because the two projects deploy separately:

- `website/static/logo.svg` (this site's favicon)
- `web/src/main/resources/web/public/static/logo.svg` (the application's favicon)

**There is no third copy acting as a source, so a new logo has to be written to
both paths.** The root of the repository deliberately holds no logo file.

`LogoMark.svelte` inlines the same geometry for the nav and footer with two
deliberate differences, so one mark can sit anywhere on the page:

- the white background square is dropped;
- the fill is `currentColor`, which is how the footer renders it light on black.

Its `viewBox` is tightened from the source file's `0 0 1254 1254` to the
artwork's measured bounds, `90 124 1075 997`, so the stone fills the box it is
given instead of floating inside the file's padding. The artwork is slightly
wider than tall, so the component derives its height from the width rather than
assuming a square. The application sidebar inlines the identical path and
viewBox in `main.html` for the same reasons.

The wordmark is set in tracked capitals, `PRÜFSTEIN`, matching the all-caps
label idiom used throughout the site and the application.

## Icons

The faceted mark does not survive a 16px favicon: the facet lines collapse into
grey noise, and no amount of cropping or background colour rescues them. So the
icons split in two.

| File | Art | Used at |
| --- | --- | --- |
| `favicon.ico` | silhouette, black on accent | 16 / 32 / 48 px, declared first so browsers that rasterise at 16px pick it |
| `favicon.svg` | silhouette, black on accent | any size, for browsers that prefer SVG |
| `apple-touch-icon.png` | full faceted mark on accent | 180px, where the facets are legible and worth having |
| `logo.svg` | full faceted mark, black on white | the source file; inlined by `LogoMark.svelte` and by the app sidebar |

The tile is square (`viewBox="90 85 1075 1075"`) because the artwork is wider
than tall and a favicon must not be squashed to fit. The accent ground is a
deliberate choice over white: it is the one colour that identifies the project
in a crowded tab strip, and it works in light and dark browser chrome alike. To
go back to black-on-white, change the `<rect>` fill in `favicon.svg` and
regenerate the `.ico` from it.

The same four files exist under
`web/src/main/resources/web/public/static/` for the application, which declares
them in `templates/main.html` (it previously declared no icon at all).

## Artwork

`static/art/` holds the same monoliths the application drops into its
background, resized from 2048px to 1200px and re-encoded (2.7 MB → ~0.7 MB, with
WebP alongside). They are decorative: `aria-hidden`, lazy-loaded below the fold,
and hidden under 1024px.
