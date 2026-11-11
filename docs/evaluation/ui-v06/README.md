# Native Compose render checks

Robolectric Android API 34, native graphics, 360 × 800 dp activity. These are host renders, not physical-phone screenshots. `07` captures the activity; `08`–`10` capture the active dialog window itself. Tests assert both save formats, progress/cancel, filename/Open/Share and visible side-by-side Play/Save controls. Dialog states are UI test fixtures, not evidence of an actual file saved on a Nothing Phone. Separate Java tests exercise real MP3 encoding, cancellation, receipt verification and offline model migration.
