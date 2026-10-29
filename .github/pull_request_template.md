# {{Title}}

## Description
<!-- Provide a clear summary of what this PR does and why. Reference relevant PRD requirements (e.g. M1, S3, L2, A3, H1-H5, I1-I6). -->

## Type of Change
- [ ] New feature
- [ ] Bug fix
- [ ] Refactoring / Hexagonal port decoupling
- [ ] UI / Liquid Glass styling & design tokens
- [ ] Documentation update
- [ ] Localization / i18n
- [ ] Tests / Mock adapters
- [ ] Build / Amper configuration

## Hexagonal Architecture & Invariants Check
- [ ] **Core Domain Purity:** Zero platform imports (no Android, UIKit, MapLibre, or SQL) in `domain/`.
- [ ] **Port Contracts:** Changes to external capabilities go through Inbound/Outbound port interfaces.
- [ ] **Privacy Invariant:** Zero coordinates or commute schedules leaked or sent off-device.
- [ ] **Battery Invariant:** GPS tracking stops immediately when disarmed, dismissed, or outside active window.
- [ ] **Bilingual Parity:** Both `en` and `id` strings exist with matching placeholder keys.
- [ ] **Theme Parity:** Tested in both Light (`Liquid Glass`) and Dark (`Nocturnal Obsidian Glass`) modes.

## Changes Made
<!-- List the specific changes made. Be precise and concise. -->
- 
- 
- 

## Validations
<!-- Concrete evidence of verification: unit tests passed, simulated commute runs, and visual checks. -->
- [ ] `./kotlin test` passed with fresh run.
- <!-- Paste test summary or attach screenshots/logs below -->
