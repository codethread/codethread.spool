# Source and workspace map

This is a **machine-local discovery map**, not universal paths or permission to operate. Reconfirm directories and `mill weaver list` on each host. The owning workspace's `.millstrand/deps.edn` and transitive pins determine active code; sibling main can differ. Read owner AGENTS.md and relevant README before sources. Use live help's source provenance to locate the installed implementation.

| Owner | Local checkout | Workspace candidate | Investigate here |
| --- | --- | --- | --- |
| Millstrand | /Users/ct/dev/projects/skein-src | /Users/ct/dev/projects/skein-src/.millstrand | CLI, Weaver, storage, activation; docs/reference.md |
| Millstrand UI | /Users/ct/dev/projects/millstrand-ui | /Users/ct/dev/projects/millstrand-ui/.millstrand | Dashboard and repository delivery policy; docs/auto-run.md |

If a checkout is missing, use the upstream links in repository AGENTS.md for discovery, then resolve the active pin before relying on behavior. Do not create a workspace just because a checkout exists.

Keep running in the hub cwd while targeting the owning board explicitly:

```nu
# $ws is the owning repository's workspace — one of the two above
strand --workspace $ws prime kanban
strand --workspace $ws kanban board
strand --workspace $ws kanban card $card
# Only after confirming that the owning repository has the requested work:
strand --workspace $ws kanban add $title --lane refinement --body $body
```
