# Security Policy

## Supported versions

The latest `main` branch is the only supported version. This project ships as
a hobby utility; there are no backported security patches.

## Scope

Donut Client is a **client-side Minecraft mod**. It:

- runs entirely on your machine, in your Minecraft client process;
- sends only the packets the vanilla client would send (movements from real
  camera state, interactions through the vanilla `interactionManager` path);
- stores its config, profiles and build-session files under your game
  directory (`config/`, `schematics/`).

It does **not** include anti-cheat evasion, packet spoofing, or xray features.

## Reporting a vulnerability

Open a private security advisory via GitHub ("Report a vulnerability" on the
Security tab), or contact a maintainer directly. Please include:

- affected commit or version;
- a minimal reproduction (steps or save file);
- expected vs actual behavior.

Do not open public issues for exploitable bugs.

## What is not in scope

- Server-side rules about client mods (that is between you and each server).
- Damage caused by running builds from third parties — only use jars you built
  yourself or obtained from this repository's official releases.
