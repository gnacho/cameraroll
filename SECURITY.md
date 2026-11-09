# Security Policy

## Supported Versions

Only the latest release receives security fixes. Run the current version.

## Reporting a Vulnerability

Please report security issues privately to hnacho@proton.me, not as public
GitHub issues. Include:

- The affected version.
- Steps to reproduce or a proof of concept.
- The impact you see (what an attacker could do).

You can expect an acknowledgement within a few days. If the issue is
confirmed, the fix ships in the next release and you get credit in the
changelog unless you prefer otherwise.

## Scope Notes

CameraRoll is a local Android app that syncs over WebDAV to a server you
configure. It stores metadata in Room and account credentials in DataStore on
the device. Problems in your Nextcloud or OpenCloud server belong to those
projects, but if you are unsure where a problem lives, report it here anyway
and it will be routed.
