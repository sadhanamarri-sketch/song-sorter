# TuneSort

Sorts downloaded songs into genre folders using tempo (on-device BPM estimate)
and lyrics (keyword matching), so Poweramp picks up the new organization
automatically. Built to sit *alongside* the "Lyrics for Poweramp" plugin,
not replace it — that app fetches/saves lyrics, this app reads what it saved.

## How to open
1. Open this folder (`TuneSort/`) directly in Android Studio (File → Open).
2. Let Gradle sync — it will pull Compose/AndroidX from Maven Central.
3. Run on a device/emulator with API 26+.

## How it works
1. **Pick a folder** — grant access to your Music/Download folder via the
   system folder picker (Storage Access Framework). TuneSort scans it
   recursively for audio files (mp3/flac/m4a/aac/ogg/wav/opus). "Only scan
   new songs" is on by default — it skips TuneSort's own genre and
   `Playlists` folders from a previous run, so re-scanning the same root
   folder doesn't re-process (and rename) already-sorted songs. Turn it off
   to force a full re-scan of everything. Toggling it doesn't re-scan by
   itself — tap **Rescan** (shown once a folder's picked) to apply it.
2. **Lyrics** — for each song it looks for a sidecar `.lrc`/`.txt` file with
   the same name in the same folder (what "Lyrics for Poweramp" saves when
   you use its file-based storage mode), and if that's missing, tries to
   read an embedded ID3 `USLT` lyrics frame (mp3 only, if the plugin embedded
   instead of saving a file).
3. **Tempo** — decodes the first ~60 seconds of audio (skipping a short
   intro) via `MediaExtractor`/`MediaCodec`, builds an energy-onset envelope,
   and autocorrelates it to estimate BPM. This is a heuristic, not
   beat-tracking-grade accuracy — expect it to land within roughly ±15%.
4. **Genre** — scores the lyrics against word lists in `GenreKeywords.kt`
   (fully yours to edit/expand), nudged by the tempo bucket (fast → Gospel/
   Christian Hip-Hop/Christian Rock, slow → Hymns/Worship). Ships tuned for a
   Christian music library — Worship/Praise, Hymns, Gospel, Contemporary
   Christian, Christian Hip-Hop/Rap, Christian Rock/Pop, and Christmas.
   Songs with no lyrics found fall back to a tempo-only guess.
5. **Review** — every song shows its detected BPM, lyrics source, and
   assigned genre with a dropdown to override it before applying anything.
6. **Apply** — by default, songs are *moved* (copy + delete original) into
   `<your folder>/<Genre>/filename.ext`. This never touches file contents,
   so it can't corrupt anything, and works for every format. Apply also
   (re)generates Poweramp playlists in `<your folder>/Playlists/`, one per
   genre+tempo combination present (e.g. `Worship-Praise - Fast.m3u8`) —
   Poweramp auto-detects standalone playlist files on its next library scan.

## Optional: writing the genre into MP3 tags too
There's a toggle to also rewrite the ID3v2 `TCON` (genre) frame in place for
MP3 files, using a small hand-written frame-preserving writer
(`Id3Tag.kt`) — it keeps every other tag (title, artist, album art) intact
and only replaces the genre frame. It's off by default. It does **not**
support FLAC/M4A/OGG — those formats are sorted by folder only unless you
wire in a real tag library (there's a commented dependency on
[Kyant0/taglib](https://github.com/Kyant0/taglib) in `app/build.gradle.kts`
to extend `TagWriter` yourself).

## Known limitations / things worth tuning
- BPM detection is a basic autocorrelation heuristic, not a trained model —
  good for slow/mid/fast bucketing, not for precise BPM display.
- The keyword genre lists are a starting point; edit
  `GenreKeywords.kt` to match your own library's vocabulary and add/remove
  genre categories.
- ID3 tag writing only covers MP3 and only touches the genre frame.
- No undo for "Apply" — it does a real file move. Test on a small folder
  first, and consider backing up your music before running it on your full
  library.
