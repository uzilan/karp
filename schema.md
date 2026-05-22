# Karp Wiki Schema

## Page Naming
- Use kebab-case slugs (e.g., `machine-learning`, `api-design`)
- One topic per page
- Prefer specific over general (e.g., `python-decorators` over `python`)

## Page Structure
Every page must have:
1. H1 title
2. One-sentence summary
3. Body content
4. `## Related` section with [[links]] to related pages

## When to Create vs Update
- Create: topic has no existing page
- Update: topic page exists — merge new info, don't duplicate

## Cross-References
Use `[[page-name]]` syntax to link related pages.

## index.md
Always update `index.md` with new sources added. Format:
`- [Source Name](source://filename) — brief description — tags: #tag1 #tag2`

## log.md
Do not modify log.md — it is append-only.

## Tags
Use lowercase, hyphenated tags. Common: #finance, #api, #architecture, #research, #howto
