# Deploy link

The record that links a collector upload (a Cloud Storage folder) to the GCP deploy it corresponds to.

## Model

A deploy link carries the project ID, deploy type, service id, version id, location (null for App
Engine), storage folder, user email, collection timestamp, deploy timestamp and the deploy's public URL.

## Redeploys (version id reuse)

When the linking logic matches a deploy whose identity already has a link, it compares the existing
link's deploy timestamp with the matched deploy's creation time:

- **Equal** — it is the very same deploy (e.g. a duplicate trigger): the existing link and its storage
  folder are left untouched.
- **Different** — a new deploy reused the version id: the new link replaces the old one and the replaced
  link's storage folder is deleted. The save happens first, so a deletion failure can only leave an
  orphaned folder as debris — never a link pointing at a deleted folder (same tradeoff as the cleanup
  sweep).

Known limitation: the cleanup sweep still judges staleness by identity only, so it cannot detect a link
whose version id still exists in GCP but whose deploy was replaced outside the portal's linking (e.g.
the collector was never run for the new deploy). Comparing the deploy timestamp against the listing was
deliberately not done: redeploying over an existing App Engine version id updates the version in place,
and its creation-time semantics would risk false deletions.
