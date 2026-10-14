# Deployment link

The record that links a collector upload (a Cloud Storage folder) to the GCP deployment it corresponds to.

## Model

A deployment link carries the project ID, deployment type, service id, version id, location (null for App
Engine), storage folder, user email, collection timestamp, deployment timestamp and the deployment's public URL.

## Redeploys (version id reuse)

When the linking logic matches a deployment whose identity already has a link, it compares the existing
link's deployment timestamp with the matched deployment's creation time:

- **Equal** — it is the very same deployment (e.g. a duplicate trigger): the existing link and its storage
  folder are left untouched.
- **Different** — a new deployment reused the version id: the new link replaces the old one and the replaced
  link's storage folder is deleted. The save happens first, so a deletion failure can only leave an
  orphaned folder as debris — never a link pointing at a deleted folder (same tradeoff as the cleanup
  sweep).

Known limitation: the cleanup sweep still judges staleness by identity only, so it cannot detect a link
whose version id still exists in GCP but whose deployment was replaced outside the portal's linking (e.g.
the collector was never run for the new deployment). Comparing the deployment timestamp against the listing was
deliberately not done: redeploying over an existing App Engine version id updates the version in place,
and its creation-time semantics would risk false deletions.
