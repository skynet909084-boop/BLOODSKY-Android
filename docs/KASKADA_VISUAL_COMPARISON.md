# KaScada Craft 1.0.7 vs BLOODSKY Visual Parity & Evidence Audit

## Overview
This document presents the visual-fidelity audit and side-by-side evidence comparison between original KaScada Craft 1.0.7 specifications (decompiled layout structures, resources, and component catalogs) and the BLOODSKY Android implementation.

## Visual Fidelity Checkpoints

### 1. Main Application Menu & Screen Layout
| UI Feature | KaScada Craft 1.0.7 Reference | BLOODSKY Implementation | Fidelity Status |
|------------|--------------------------------|-------------------------|-----------------|
| Home Menu Header | Top title "KaScada" / "Craft" with subtitle and dark theme background | "BLOODSKY" title, "HMI / SCADA" subtitle, 35,35,35 dark theme | partial (layout matched, brand labels adapted) |
| Menu Buttons | Metallic grey gradient rounded buttons (#BEBEBE - #848484) with dark stroke (#4C4C4C) | `legacyMenuButton` with identical gradient, corner radius, padding, and text styling | partial |
| Screen Navigation Bar | Left/Right navigation arrows, Screen Title rename button, "+", "−" buttons, and background color selector | Integrated `screenBar` with `◀`, Screen Name, `Фон`, `▶`, `+`, `−` buttons | partial |

### 2. Editor Toolbar & Operator (Work) Mode
| Workflow State | KaScada Craft 1.0.7 Reference | BLOODSKY Implementation | Fidelity Status |
|----------------|--------------------------------|-------------------------|-----------------|
| Mode Switcher | Work mode toggle ("Работа" vs "Редактирование") | Top bar mode toggle button ("▶ Работа" vs "✏️ Редакт.") switching `isOperatorMode` | partial |
| Edit Mode Canvas | Grid alignment lines, widget selection border with corner resize grip, wire mode status indicator | Canvas grid lines, selection border with corner grip, wire mode guidance header | partial |
| Operator (Work) Mode | Operational view: grid lines hidden, resize grips hidden, edit toolbars collapsed, direct single-tap widget interaction | `isOperatorMode = true`: grid and selection handles hidden, edit toolbars collapsed, single-tap runtime execution | partial |

### 3. Component & FBD Picker with Preview Thumbnails
| Category | KaScada Craft 1.0.7 Picker | BLOODSKY Implementation | Fidelity Status |
|----------|----------------------------|-------------------------|-----------------|
| Picker Layout | Scrollable dialog grouped under "КОМПОНЕНТЫ И ИНДИКАТОРЫ" and "ФУНКЦИОНАЛЬНЫЕ БЛОКИ (FBD)" | Scrollable dialog with category section headers and 44x44dp preview thumbnails | partial |
| Widget Thumbnails | Mini visual previews for Buttons, Indicators, Setpoints, Gauges, Progress Bars, Trends, Alarms, Photos | `createWidgetThumbnail` rendering mini canvas previews for widget classes | partial |
| FBD Thumbnails | Grey rectangular block previews (#888888 body, black outline) with block names | Grey 44dp FBD block thumbnails (#888888 body, black outline) with centered block title text | partial |

### 4. Component Rendering & Multi-State Parity
| Component Type | Visual Representation & States | BLOODSKY Canvas Rendering | Fidelity Status |
|----------------|--------------------------------|---------------------------|-----------------|
| CustomLabel | Custom text, background color, font size, auto-fit text option | Rectangular text canvas with custom background, text color, and auto-resize | partial |
| Button Control | Dual state (ON/OFF), pressed styling, Modbus write integration | Dynamic button face with [ВКЛ]/[ВЫКЛ] status and register write-back | partial |
| Indicator / LED | Dual state LED lens, ON (Green), OFF (Red), Error/Config (Yellow) | Circular LED lens with live register state color | partial |
| Setpoint Adjuster | Dual button controls (− and +) with numeric display frame | Framed setpoint with interactive − and + touch targets and register write-back | partial |
| Dynamic Text | 16 discrete states with custom string labels and background colors | `dynTextStates[16]` and `dynTextColors[16]` dynamic indexing based on live register | partial |
| Analog Gauge | Arc dial scale, min/max ticks, dynamic red needle pointer | 180° arc gauge with needle calculation and scaled live value display | partial |
| Progress Bar | Level bar container, percentage/scaled value fill | Horizontal progress bar with ratio fill and formatted float text | partial |
| Trend / Chart | Grid axis lines, real-time trend curve plot | Dark container with vector trend line and label | partial |
| Bit List Widget | Bit mask indicators (Bits 0..15) with individual state LED dots | Multi-bit list with hex mask display and bit state LED indicators | partial |
| Sound / Alarm | Edge-triggered audio trigger, speaker badge, threshold value | Speaker badge, edge-triggered MediaPlayer / ToneGenerator playback | partial |
| Screen Link | Navigation jump button with link icon and target screen label | Screen link target jumper with single-tap screen transition | partial |
| Multi-state Photo | 16 image state slots, URI/File bitmap decoding, fallback frame | Bitmap caching with URI/File decoding and state fallback status text | partial |
| Voice Announcement | 16 voice text messages, TextToSpeech synthesis, edge-triggered | Russian TextToSpeech engine synthesis edge-triggered on state change | partial |

### 5. FBD Block Style & Inter-Block Wiring
| Feature | KaScada Craft 1.0.7 Reference | BLOODSKY Implementation | Fidelity Status |
|---------|--------------------------------|-------------------------|-----------------|
| Block Box Geometry | Rectangular box, #888888 grey body with black contour stroke, header title, left input pin dots, right output pin dot | Rectangular box, #888888 grey body, black contour, title, left input pin dots, right OUT pin dot | partial |
| Wire Rendering | Stepped lines between source output pin and target input pins | Stepped 3-segment orthogonal vector wire paths in purple (#7C3AED) | partial |
| Cycle Guard | Immediate rejection of feedback loops with clear user error notification | DFS topological cycle detection preventing circular wire connections | Matched |

## Residual Deviations & Verification Agenda
1. **Side-by-Side Screenshot Audits**: Visual states are marked `partial` or `unverified` until actual runtime screenshots and decompiled drawables are compared pixel-by-pixel.
2. **Resource Binding**: Verify runtime binding of `kc_*` drawables from `craft-reference`.
