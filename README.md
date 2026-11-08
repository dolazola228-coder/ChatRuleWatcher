# Chat Rule Watcher (Fabric client mod)

Client-side Minecraft Java mod intended for Fabric 1.21.11. It reads player chat locally and displays moderation alerts only to the person running the mod. It does not mute, kick, or ban anyone.

## Included checks
- 3.1: a starter list of common insults. The supplied exception words are kept in the source as the requested exclusion list; they are not treated as insults by this starter detector.
- 3.2: more than 70% uppercase letters, with at least 8 letters.
- 3.3: mentions of relatives.
- 3.4: repeated messages / high message frequency.
- 3.5: URLs, Discord/Telegram links, domains, and IP:port-like text.
- 3.6: a small phrase list for server/admin criticism.
- 3.7: phrases that may indicate admin impersonation.
- 3.8: a small keyword list for political/aggressive topics.
- 3.9: a small phrase list for encouraging rule-breaking.
- 3.10: a small phrase list for begging.

## Important limitations
This is a heuristic starter project, not a semantic AI classifier. Rules 3.3, 3.6, and 3.8 can create false positives because a word alone does not prove a violation. Expand the phrase lists in `ChatRuleWatcher.java` to fit your server. Rule 3.1 exception words are not a complete Russian insult dictionary. No mod can reliably infer all context from keyword lists alone.

## Build
Requires Java 21 and Gradle. From this directory run:

```sh
gradle build
```

The built mod jar will be in `build/libs/ChatRuleWatcher-1.0.0.jar`.

Install the jar in your Minecraft client's `mods` folder along with Fabric Loader and Fabric API for Minecraft 1.21.11. This project has not been compiled in this environment, so dependency/version compatibility should be verified during the build.
