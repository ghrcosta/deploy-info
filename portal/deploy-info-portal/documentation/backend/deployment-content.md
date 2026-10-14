# Deployment content — file-content capability

Reads the collected files (GIT section and extras) out of a collector upload's Cloud Storage folder, and
sends it to the frontend.

## How it works

- Input: the upload's `storageFolder` (carried by the deployment tree's version nodes in the frontend)
- All files are read from Cloud Storage. The uuid map is parsed RFC 4180-style (quoted file paths
  survive commas/quotes); rows with a wrong shape or an unknown status, and entries referencing files
  missing from the folder, are skipped.
- Output: the content (git section + extras), "folder not found" or "storage error".
- Files are represented with their original repository path, their stored text and their git state
  (`new`/`untracked`/`modified` per the collector's git status; always `modified` for extras).

## Storage access

The content is read through a storage reader interface that works with the folder name only (the bucket
is an infrastructure concern); its Cloud Storage implementation reads blobs with the Spring Cloud GCP
Storage starter, is resolved lazily (the portal starts without Storage credentials) and wraps all
failures in a storage-read exception.
