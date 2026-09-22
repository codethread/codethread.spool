# Agent blocker rollout

The canonical [discriminated union and patterns](auto-run.md#agent-blocker-contract)
replace the earlier independent failure and decision flags. Dispatcher admission,
receipts, capacity and scheduling remain unchanged.

## Delivery order

1. Publish the Millstrand weave update support and Codethread reporting changes.
2. Rework the preserved Millhouse pilot against that basis. Present its diff for
   user review before rolling out any other consumer.
3. After pilot approval, update Millstrand, Harnesses and Millstrand UI using the
   source maps saved on their feature cards.

| Repository | Remaining source work |
| --- | --- |
| Millhouse | Update the preserved pilot's pins and reporting import; select the three patterns and label hook; replace its old test expectations. Keep core Land independent. |
| Millstrand | Update consumer pins and autorun module activation. The weave API extension alone does not migrate its autorun policy. |
| Harnesses | Update consumer pins and module activation; remove copied reporting prose. |
| Millstrand UI | Update pins and module activation; include `agent-blocked` and `needs-decision` in autorun attention filtering. Preserve unrelated inspection findings and human-attention uses. |

Each consumer selects `ct.spools.codethread.auto-run-reporting` patterns and its
`derive-labels` hook. Repositories using the optional autonomous landing helper
import `ct.spools.codethread.auto-run-land`. Standalone Land has no reporting
requirement. Use the published revisions recorded on the epic and feature cards.

## Runtime activation

Source publication does not update running Weavers or frozen assignments.
The coordinator is authorized to update dependency pins and restart Weavers,
including aligning sibling dependencies together when needed to clear blockers.
This does not expand the policy rollout beyond the Millhouse review checkpoint.

At cutover, inspect active runs and existing reporting attributes. Where a
current blocker must be retained, save or identify
its evidence strand and publish exactly one new variant; remove the obsolete
flags, question/role attributes and failure display label as part of that
reviewed conversion. Retain the evidence itself. Do not infer a failure from
an ordinary checkpoint or an unrelated UI inspection result.

Verify pattern discovery and reporting in a disposable world first. Existing
running assignments retain their frozen guidance until an
explicitly planned handoff or completion.

The prior Millhouse documentation PR overlaps this cleanup; record its
supersession on the Millhouse feature before deciding its disposition.
