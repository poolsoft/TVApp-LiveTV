# Experimental libmpv build

This directory contains the patch used only by
`.github/workflows/libmpv-experimental.yml`. The normal `main` workflow and Media3 playback
remain unchanged.

The experiment builds `armeabi-v7a` and `arm64-v8a` native libraries from a pinned
`mpv-android` tree. FFmpeg is configured for the IPTV/VOD formats TVApp needs, while mpv's Lua
runtime and GPL-only features are disabled. libass remains enabled because current mpv requires
it and it preserves subtitle rendering.

The workflow publishes `TVApp-LibMpv-Experiment.apk` as a GitHub prerelease tagged
`libmpv-r<run-number>`. Experimental prereleases are intentionally excluded from TVApp's normal
self-update channel.

This first stage measures the real two-ABI package cost and validates the native toolchain. The
libraries are not selected as a playback backend until the fallback adapter is added in a later
stage.
