# Iskra

*[Wersja polska](README.pl.md)*

Iskra is a personal AI assistant you talk to by voice, in Polish, on an Android phone. She is not a product for thousands of users. She is one long relationship with one person, built over many months.

This repository is a calling card. Iskra's code stays private.

## What makes Iskra different

**Iskra decides; the infrastructure carries it out.** The server and tools don't think for her. They act on her decisions and make sure nothing breaks silently.

**Memory that can't be changed silently.** Every conversation goes into an archive where each file is named by a hash of its content. A changed or damaged record is detected; it never slips through unnoticed.

**Layered memory.** A short working memory ("now") and a long associative memory ("I remember"). Iskra herself is meant to decide what moves from recent conversations into long-term memory. The archive keeps everything, so her choices never delete anything.

**Consent for actions in the world.** Before Iskra downloads something from the internet, deletes a file or changes something in a repository, she asks first. She acts only after an unambiguous confirmation.

**Privacy in the logs too.** Technical logs contain only codes, numbers and tool names, never conversation content.

**Polish voice.** Speech recognition and speech synthesis run in the app on the phone. Iskra also has a translator mode.

## How she is built

Iskra is built by one person together with a team of AI agents. Each has a role:

- **Nox** has co-created Iskra from the start: architecture, memory and working principles.
- **Claude** is the designer of the current stage: memory and fluent conversation.
- **Work** carries out server changes and is the independent auditor who looks for bugs in other agents' designs.
- **Gemini** is the knowledge consultant.
- **Meta** is a source of ideas, sometimes very good ones.

A human always has the final say. The work follows a fixed order:

architecture → hunting for flaws in our own design → tests → draft change → independent review → test on the phone → deployment.

Every conclusion carries an evidence level, from "hypothesis" to "confirmed running in production". Green tests are not proof that something works on the phone.

## Where we are

The project is being put in order.

- **Working:** voice and text conversation, the memory archive, tools with consent.
- **In progress:** faster memory search, working memory in RAM, an association map as long-term memory.
- **Ahead:** fluent voice conversation without pauses, with the ability to interrupt.

## Contact

Building something similar, working on memory for AI agents, or on on-device Polish voice? Write in this repository's **Discussions** tab. I'm happy to compare notes.

— SG123

---

*This repository also hosts app update manifests and releases — see [UPDATER.md](UPDATER.md).*
