# Desktop icons

The desktop bundle uses the blue-background, white-mark variant of the AI Sales
Agent logo. Keep the transparent rounded corners when regenerating assets.
The website logo and mobile icon assets are intentionally independent.

## Source and generation

`frontend/src-tauri/icons/icon.png` is the canonical desktop bitmap. The blue
variant was edited with the built-in image generation tool using this prompt:

> Change only the dark background to an azure-blue/cyan gradient. Preserve the
> white ascending bars, orbit and star, their position and scale, and transparent
> rounded corners. No text, border or outer shadow.

To regenerate platform sizes without overwriting mobile assets, run from
`frontend`:

```powershell
pnpm exec tauri icon src-tauri/icons/icon.png --output test-results/desktop-icons
Get-ChildItem -LiteralPath test-results/desktop-icons -File |
    Copy-Item -Destination src-tauri/icons
```

Check the small PNG previews and the multi-resolution `icon.ico` before
committing. `tauri.conf.json` references these desktop assets for packaging.
Already installed applications receive the new icon only with a newly built
desktop package; pushing source code alone does not update installed shortcuts.
