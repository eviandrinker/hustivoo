# Hustivoo design system

The interface is designed around quick decisions: business health, attention
items and clear paths into each module. The shell uses a 228px white sidebar,
a compact topbar and a warm background. Content is limited to 1280px, with
32px desktop, 24px tablet and 16px mobile side padding.

Tokens live in `src/index.css`; shared and responsive styles are in
`src/App.css`. The existing Tailwind/shadcn setup is retained without new
libraries. Inter is preferred when locally available, with Helvetica/Arial
fallbacks. Titles use 26–30px, sections 16–18px, body 14px, labels 12–13px
and financial figures 24–30px with tabular numerals.

## Components

- `button.tsx`: primary, secondary, tertiary and destructive native buttons.
  Buttons default to `type="button"`; use `type="submit"` explicitly in forms.
- `forms.tsx`: Input, Textarea, Select, Checkbox and FormField. FormField accepts
  one control, creates a label association and connects hints/errors through
  `aria-describedby`. Pass `required` to the control when applicable.
- `display.tsx`: PageHeader, SectionHeader, StatusIndicator,
  InformationPanel, EmptyState, LoadingState and ErrorState. Status must always
  include text, never color alone. Use named sections for page structure.
- `data-table.tsx`: typed columns and rows, right-aligned numeric columns,
  accessible caption and empty state. The focusable wrapper scrolls on small
  screens. Row actions should be native buttons/links with record-specific names.
- `dialog.tsx`: controlled native modal Dialog and ConfirmationDialog. Native
  `showModal()` supplies focus containment and makes the background inert.
  Escape requests closure; cleanup restores the opener's focus. Confirmation
  calls `onConfirm`; the caller owns completion, failures and closing. Never
  connect these primitives to mutations without implementing those states.
- `card.tsx`: retained shadcn primitive, with reduced rounding and no shadow.

Example field:

```tsx
<FormField label="Customer name" hint="Use the business or trading name." error={error}>
  <Input name="customerName" required />
</FormField>
```

- `metric-card.tsx`: compact label/value/context display for demo business metrics.
- `Sidebar.tsx`: shared navigation with active links, mobile disclosure and Escape support.

## Responsive behavior

At 1280–1440px, four metrics sit above the revenue/attention columns. Below
1100px, metrics use two columns and attention items move beneath the chart.
Below 850px, navigation becomes an inline disclosure. On mobile, projects use
compact two-row summaries, actions stay visible and chart labels retain readable
font sizes. The dashboard has no horizontally scrolling tables. The reusable
data table keeps a scroll wrapper for future data-heavy modules.

## Scope

Dashboard financial data remains explicitly demo-only. The live backend health
indicator is separate. Existing API modules, routes and backend are unchanged.
Forms and dialogs are primitives for future workflows, not pretend working
business features. Browser visual and keyboard checks remain necessary before
production release; compilation does not establish visual accessibility.
