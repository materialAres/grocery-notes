# Sticky Notes Widget App — High-Level Design

> App name: **TBD** (placeholder `com.example.stickers`)
> Audience: personal use (sideloaded), English UI

## 1. Product summary

A home-screen-first sticky notes app. **Every note is a widget**: a note is created by placing a widget and deleted (to a 7-day bin) by removing it. Notes mix free text and checklist items. A checked item disappears for good.

### Core requirements (confirmed)

| Area | Decision |
|---|---|
| Widget sizes | One resizable widget, 3–5 columns × 3–4 rows (covers 3x3, 4x3, 4x4, 5x4; 3x4 and 5x3 are allowed too) |
| Note ↔ widget | 1 note = 1 widget. Removing the widget moves the note to the bin |
| Note content | Optional **title** (bold, same size as the body) + an ordered mix of **text blocks** and **checklist items** |
| Widget interaction | Check items directly on the widget. Any other tap opens the full editor |
| Overflow | Widget content scrolls vertically |
| Checked item (widget) | **Deleted permanently and immediately** |
| Checked item (editor) | Shown struck through while editing (can be unticked), **deleted when leaving the editor** |
| Item order | New items are added at the bottom. Long-press and drag to reorder |
| Item text | Single line, wraps visually. Enter creates the next item |
| Appearance | Color picker per note. Colors stay the same in dark mode (only the app UI goes dark) |
| Text size | Per note (Small / Medium / Large). Title and body use the same size |
| Storage | Local only, plus a recently-deleted bin (7-day auto-purge, can be emptied by hand) |
| Widget setup | Setup screen on placement: **New note** or **Restore deleted** |
| Restore | From the widget setup screen **or** from the in-app bin (pins a new widget) |
| App entry | Minimal launcher screen: how-to, "Add note to home screen", recently deleted |

## 2. Tech stack

- **Language:** Kotlin (Google's primary language for native Android)
- **Min SDK:** 31 (Android 12). **Target SDK:** latest stable
- **App UI:** Jetpack Compose + Material 3
- **Widget UI:** Jetpack Glance (`glance-appwidget`). Compose-style API compiled to RemoteViews, with native `CheckBox` and scrollable `LazyColumn`
- **Persistence:** Room (SQLite) with Kotlin Flows
- **Background work:** WorkManager (bin purge)
- **DI:** manual or Hilt (small app, so a simple manual `AppContainer` is enough)
- **Architecture:** single module, MVVM (ViewModel → Repository → Room DAO)

Why Android 12+: it adds native widget checkboxes, `targetCellWidth/Height` for grid-based sizing, and rounded widget corners. Supporting older versions would need workarounds for all three.

## 3. Architecture overview

```
┌──────────────────────────── App process ────────────────────────────┐
│                                                                      │
│  UI layer (Compose)                Widget layer (Glance)             │
│  ├─ LauncherActivity               ├─ NoteWidget (GlanceAppWidget)   │
│  │   ├─ How-to / Add note          ├─ NoteWidgetReceiver             │
│  │   └─ Recently deleted (bin)     │   └─ onDeleted → move to bin    │
│  ├─ WidgetSetupActivity            └─ CheckItemAction (ActionCallback)│
│  │   (New note / Restore)                └─ delete item, refresh     │
│  └─ EditorActivity                                                   │
│        │                                   │                         │
│        ▼                                   ▼                         │
│  ViewModels ─────────────► NoteRepository ◄─────────────             │
│                              │   └─ after every write:               │
│                              │      NoteWidget.update(widgetId)      │
│                              ▼                                       │
│                         Room database                                │
│                                                                      │
│  WorkManager: BinPurgeWorker (daily) — deletes bin notes > 7 days    │
└──────────────────────────────────────────────────────────────────────┘
```

**Single source of truth:** the Room database. The widget never holds its own state. It reads from the repository on every refresh, and every write goes through `NoteRepository`, which then triggers a widget update.

## 4. Data model

```kotlin
enum class NoteColor { YELLOW, PINK, ORANGE, GREEN, BLUE, PURPLE, GRAY, WHITE }
enum class TextSize { SMALL, MEDIUM, LARGE }          // e.g. 14sp / 16sp / 18sp
enum class BlockType { TEXT, CHECK }

@Entity
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appWidgetId: Int?,        // unique when not null; null while in the bin
    val title: String,            // "" = no title shown
    val color: NoteColor = NoteColor.YELLOW,
    val textSize: TextSize = TextSize.MEDIUM,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?          // not null = in the recently-deleted bin
)

@Entity(foreignKeys = [ForeignKey(Note::class, ["id"], ["noteId"], onDelete = CASCADE)])
data class Block(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val position: Int,            // ordering within the note
    val type: BlockType,
    val text: String,
    val checked: Boolean = false  // only used for the editor's struck-through state
)
```

- **Active note:** `deletedAt == null && appWidgetId != null`
- **Bin note:** `deletedAt != null`
- **Widget rendering** always filters out `checked == true` blocks, so a tick in the editor never shows on the widget even before it is purged.

## 5. Key flows

### 5.1 Create a note (place widget)
1. User long-presses the home screen → Widgets → picks the app's widget (default 3x3).
2. The launcher opens **WidgetSetupActivity** (declared as `android:configure`).
3. The screen offers **New note** or **Restore deleted** (the restore list is hidden if the bin is empty).
   - *New note:* insert an empty `Note` bound to the `appWidgetId`, return `RESULT_OK`, open the **EditorActivity**.
   - *Restore:* set the chosen bin note's `appWidgetId` and clear `deletedAt`, return `RESULT_OK`.
4. If setup is cancelled, return `RESULT_CANCELED`, and the launcher drops the widget.

`widgetFeatures="reconfigurable"` also lets the user re-open setup later from the widget's long-press menu (launcher-dependent).

### 5.2 Check an item on the widget
1. Each `CHECK` block is a Glance `CheckBox` with `onCheckedChange = actionRunCallback<CheckItemAction>(blockId)`.
2. `CheckItemAction` → `repository.deleteBlock(blockId)` → `NoteWidget.update(glanceId)`.
3. The item vanishes. There is no undo.

### 5.3 Edit a note
- Tapping the title, a text block or empty space on the widget opens `EditorActivity(noteId)`.
- The editor contains:
  - **Title field** (optional, single line, bold)
  - **Block list:** text blocks (multi-line paragraphs) and checklist items (single line, Enter → new item below, Backspace on an empty item → delete it)
  - **Toolbar:** "+ checklist item", "+ text", "turn this line into checklist ↔ text", color picker, text size (S/M/L)
  - **Drag handle** on each block (long-press and drag to reorder)
- Ticking an item in the editor sets `checked = true` (shown struck through, can be unticked).
- **On leaving the editor** (`onStop`, i.e. back, home or app switch): delete all `checked` blocks, save, refresh the widget.
- Edits are saved with debouncing (~300 ms) so the widget stays current and nothing is lost if the process is killed. Any leftover `checked` blocks are also purged the next time the editor opens.

### 5.4 Remove a widget → bin
- `NoteWidgetReceiver.onDeleted(ids)` → for each id: set `deletedAt = now`, `appWidgetId = null`.
- Completely empty notes (no title, no blocks) are deleted outright instead of going to the bin.

### 5.5 Restore from the bin (in app)
1. LauncherActivity → Recently deleted → tap **Restore** on a note.
2. Call `AppWidgetManager.requestPinAppWidget(provider, extras, successCallback)`, with the note id in the callback `PendingIntent`.
3. The launcher shows its "Add to home screen" dialog. The success callback (a BroadcastReceiver) receives the new `appWidgetId` → bind the note and clear `deletedAt`.
4. If the launcher doesn't support pinning (`isRequestPinAppWidgetSupported() == false`), show "Add a widget manually and choose *Restore deleted*".

**Pinned-widget note:** a widget added via `requestPinAppWidget` may still trigger the setup activity. The setup activity must detect that a pending restore/new-note binding already exists for that id and finish immediately with `RESULT_OK`.

### 5.6 Bin purge
- `BinPurgeWorker` (WorkManager, periodic, about every 24 h): delete notes with `deletedAt < now − 7 days`.
- The same purge also runs when the app opens, so the bin is accurate even if the worker was delayed.
- **Empty bin** button in the Recently deleted screen (with a confirm dialog). There is also a per-note **Delete forever**.

## 6. Widget design

**Provider config** (`res/xml/note_widget_info.xml`):
- `targetCellWidth=3`, `targetCellHeight=3` (default placement)
- `minResizeWidth/Height` ≈ 3×3 cells, `maxResizeWidth/Height` ≈ 5×4 cells (in dp, using the Android 12 cell-size formula)
- `resizeMode="horizontal|vertical"`
- `configure=".WidgetSetupActivity"`, `widgetFeatures="reconfigurable"`
- `previewLayout` showing a sample sticky note in the picker

**Layout** (the same at every size, because content scrolls):
```
┌──────────────────────────────┐  ← rounded, note color background
│ **Groceries**                │  ← bold title, same size (hidden if empty)
│ Remember the discount card   │  ← text block (tap → editor)
│ ☐ Milk                       │  ← checkbox (tick → deleted)
│ ☐ Eggs                       │
│ ☐ Bread                      │  ↕ LazyColumn scrolls
└──────────────────────────────┘
```
- **Glance `SizeMode.Exact`**: one layout that stretches to the cell size.
- **Empty note:** shows a faint "Tap to write…" placeholder.
- Text color is always dark, because note colors are light paper tones and stay the same in dark mode.

**Launcher caveat:** 5-column widths only fit on launchers whose home grid has at least 5 columns. On a 4-column grid the widget simply stops at 4 columns.

## 7. App screens

1. **LauncherActivity (home)**
   - Short how-to: "Long-press your home screen → Widgets → [App name]"
   - **Add note to home screen** button (`requestPinAppWidget`, creates a new note)
   - **Recently deleted** entry with a count badge
2. **Recently deleted:** list of bin notes (title or first line, color chip, "deletes in N days"), each with Restore and Delete forever; plus Empty bin.
3. **WidgetSetupActivity:** New note / Restore deleted list.
4. **EditorActivity:** described in §5.3. Its background uses the note's color, so it looks like the sticky note.

## 8. Project structure (proposed)

```
app/src/main/java/com/example/stickers/
├─ data/          Note.kt, Block.kt, NoteDao.kt, AppDatabase.kt, NoteRepository.kt
├─ widget/        NoteWidget.kt, NoteWidgetReceiver.kt, CheckItemAction.kt,
│                 PinWidgetCallbackReceiver.kt
├─ ui/launcher/   LauncherActivity.kt, BinScreen.kt, BinViewModel.kt
├─ ui/setup/      WidgetSetupActivity.kt
├─ ui/editor/     EditorActivity.kt, EditorScreen.kt, EditorViewModel.kt
├─ work/          BinPurgeWorker.kt
└─ App.kt         (AppContainer, schedules the purge worker)
app/src/main/res/xml/note_widget_info.xml
```

## 9. Milestones

1. **Data layer:** Room entities, DAO, repository, unit tests.
2. **Basic widget:** Glance widget showing title + blocks, scrolling, colors, text size.
3. **Widget check-to-delete:** `CheckItemAction` and refresh.
4. **Setup + editor:** WidgetSetupActivity (New note), EditorActivity with mixed blocks, check-on-exit purge, drag reorder, color and size pickers.
5. **Bin:** `onDeleted` → bin, LauncherActivity, Recently deleted screen, restore (both paths), purge worker, empty bin.
6. **Polish:** widget preview, empty states, resizing tests at 3x3 / 4x3 / 4x4 / 5x4 (and 3x4 / 5x3), testing on 2–3 launchers.

## 10. Risks & open technical points

- **Launcher differences:** resize limits, pinning support and how `reconfigurable` behaves vary by launcher (Pixel, Samsung One UI, Nova…). Test on the launcher(s) you actually use.
- **Fast double taps** on widget checkboxes: each callback carries the block id, so deleting an id that is already gone is a safe no-op.
- **Widget update throttling:** Glance updates are async. A short delay (~100–300 ms) between tick and disappearance is expected.
- **Phone migration/backup:** out of scope (local only). Note that widget ids change after a device restore. If auto-backup is wanted later, handle `onRestored` id remapping.

## 11. Decisions I filled in (please confirm or change)

These were not specified, so I picked defaults:

1. **Palette:** 8 colors (yellow, pink, orange, green, blue, purple, gray, white). New notes default to yellow.
2. **Text sizes:** Small/Medium/Large = 14/16/18 sp (scaled by the system font setting). Default Medium.
3. **Text vs checklist editing:** toolbar buttons "+ checklist item", "+ text" and a toggle to convert the current line. Text blocks can be multi-line paragraphs, while checklist items are single-line.
4. **Drag reorder** applies only to checklist items.
5. **Empty notes** (no title, no content) skip the bin when their widget is removed.
6. **Widget taps:** tapping anywhere except a checkbox opens the editor.
7. **Save model:** auto-save while typing (no explicit Save button).
8. **Bin entry display:** title, or the first line of content if there is no title.
