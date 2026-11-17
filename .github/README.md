# Aggregate SNAPSHOT publication

Pushes to `1.3`, including merged PRs, notify the `jetlinks/jetlinks` aggregate workflow.
Configure Actions Secret `JETLINKS_DISPATCH_TOKEN` using a fine-grained PAT that grants
Actions read/write access only to `jetlinks/jetlinks`; default Metadata read access is sufficient.
The same-named organization Secret may be shared with this repository instead.

The notification job confirms dispatch, not publication. The aggregate run resolves current
`1.3` sources, serializes releases and verifies every Maven upload. Maven credentials remain
in the aggregate repository. Keep token approval and expiry current; do not substitute the
repository-scoped built-in `GITHUB_TOKEN` for the cross-repository dispatch token.
