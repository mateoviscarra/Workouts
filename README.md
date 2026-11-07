# Workouts

A modern, offline-first Android workout and nutrition tracker built with Jetpack Compose, Material Design 3, and Gemini AI.

## Features

### Workout Tracking & Routine Editor
- **Custom Routines & Splits**: Push/Pull/Legs, Upper/Lower, or full custom day splits.
- **Exercise Library & Alternates**: Categorized catalog with carousel variations per exercise slot.
- **Advanced Exercise Types**:
  - Rep & weight logging with instant focus clear.
  - Bodyweight exercises with positive or negative load (e.g. assisted pull-ups).
  - Timed exercises (e.g. planks, isometric holds).
  - Supersets (Alternating set-by-set or Sequential back-to-back).
- **Session Progress**: Sticky progress indicator following as you scroll down workouts.
- **Straps & Units**: Toggle straps usage and kg/lbs per exercise.

### History & Personal Records (PRs)
- Comprehensive workout session history.
- Automatic PR detection and tracking across movements.
- Unified session finish flow.

### Nutrition & Macro Tracking
- Daily targets and real-time progress for **Calories**, **Protein**, **Carbs**, and **Fat**.
- **1-Tap Staples Carousel**: Quick-log frequent meals and snacks (e.g. Overnight Oats, Protein Alfajores).
- **Day Log by Meal Slot**: Breakfast, Lunch, Dinner, and Snacks breakdown.
- **Gemini AI Vision Scanner**: Snap meal photos or nutrition labels for automated calorie and macro estimation, featuring customizable prompts with regional Bolivian cuisine localization.

### Data Backup & Portability
- **Unified Backup (JSON)**: Full export and one-click restore of routines, workout history, saved meals, and food logs.
- **Spreadsheet Exports (CSV)**: Export dedicated CSV tables for both Nutrition and Workout History.

### Google Calendar Integration
- Sync workout logs to your selected Google Calendar.
- Detailed event descriptions with sets, reps, and weights.
- Credentials stored securely on-device.

### Appearance
- Dynamic Light, Dark, and System theme support with instant switching in Settings.

## Tech Stack
- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **Language**: Kotlin 1.9 / Coroutines / Flow
- **AI**: Google Generative Language API (Gemini Vision)
- **Storage**: SharedPreferences & Jetpack DataStore
- **Integrations**: Google Calendar API & Google Sign-In
