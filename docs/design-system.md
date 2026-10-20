# ENotes design system — initial brief

This is a starting specification, not a claim that visual assets or app components already exist. Refine after reviewing the first implemented screens.

## Product feel
Calm, focused, modern, and fast. Use a distinctive indigo identity with restrained accent color, clear hierarchy, generous spacing, and minimal visual noise. Content should remain more prominent than decoration.

## Color and themes
- Brand direction: deep indigo with a lighter indigo interactive accent.
- Support Light, Dark, and System modes.
- Define semantic tokens for background, surface, elevated surface, primary text, secondary text, border, primary action, success, warning, and error.
- Verify contrast in both themes; do not rely on color alone to communicate status.

## Typography
- Use the platform's readable sans-serif defaults unless the chosen stack supports a suitable bundled family.
- Establish a consistent type scale for display, page title, section title, body, secondary, label, and caption.
- Respect Android font scaling and avoid fixed-height text containers.

## Layout and components
- Use consistent page insets, spacing tokens, rounded surfaces, icon sizing, and touch targets.
- Shared components: app bar, bottom navigation, search field, note card, document row, tag chip, primary/secondary/text button, empty state, error state, progress indicator, confirmation dialog, bottom sheet, and snackbar.
- Prefer native Android patterns for permission prompts, file pickers, share sheets, biometric prompts, and account authorization.
- Keep controls labeled and screen-reader accessible.

## Representative review screens
1. Home dashboard
2. Note editor
3. AI assistant chat
4. AI document result
5. Document viewer/library
6. Google Drive search
7. Reminder confirmation
8. Settings with Light/Dark examples

## Visual-reference policy
Reference images are guidance, not a substitute for functional requirements. Store approved references in `design/references/`; store the interaction and state requirements in Markdown. Do not generate all 80 images before the shared visual language is reviewed.
