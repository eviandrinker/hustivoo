# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

You can also install [eslint-plugin-react-x](https://npmx.dev/package/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://npmx.dev/package/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

## Hustivoo dashboard

Use Node 22. The development server forwards `/api` to `http://localhost:8080`.
Start the Spring Boot backend separately to see the live connection indicator.

- `npm run dev`: local development
- `npm run lint`: ESLint
- `npm run build`: TypeScript project checks and production build

The app redirects `/` to `/dashboard`. The other six workspace routes currently
show placeholder pages. Production hosting must serve `index.html` for frontend
routes and route `/api` to the backend (the Vite proxy is development-only).

Financial figures, chart history, clients, invoices, and projects are demo fixtures
in `src/data/dashboard.ts`; currency is CHF. Profit is revenue less expenses,
before tax; effective hourly rate is that profit divided by tracked hours.
No business data is persisted and no authentication is implemented.

The existing `src/api` client and health service handle HTTP communication.
`src/hooks/useBackendHealth.ts` manages the shell's live connection state.
`src/layouts`, `src/pages`, and `src/components` contain the presentation layer.
Tailwind is integrated through its Vite plugin, and `components.json` configures
shadcn/ui with a local Card component and the `@/` source alias.

## Frontend authentication

`/login` and `/register` use the existing Hustivoo controls. All workspace routes
are protected. `AuthProvider` restores the browser session via `/api/v1/auth/me`
before rendering routes; a failed connection offers retry rather than incorrectly
showing a signed-out state. Authenticated visitors to auth pages go to the dashboard.
A protected destination is retained through login/registration, with redirects
restricted to known local workspace paths.

The shared API client forces `credentials: 'same-origin'` and accepts only relative
`/api/` URLs. It fetches `/api/v1/auth/csrf` before each mutation and sends the returned
`X-CSRF-TOKEN`. Tokens are held only in memory. Login and logout refresh CSRF after
success; if that refresh fails, the next mutation obtains a fresh token. No mutation
is automatically replayed. HTTP 204 is supported for logout. The browser manages
the HttpOnly session cookie; neither it nor authentication tokens are stored in
localStorage/sessionStorage.

Registration validates email, a nonblank password of at least 12 characters,
at most 72 characters/UTF-8 bytes, and matching confirmation. Success redirects
to login with confirmation; it does not automatically authenticate. Backend
validation remains authoritative. Errors are translated into safe user messages.

Authenticated 401 responses clear local identity. A 403 checks `/auth/me` to
distinguish an expired session from a rejected CSRF token; it never retries the
original mutation. Returning to a visible tab rechecks the session. Connection
failures during these background checks do not falsely sign users out. Logout
clears state only after server success (or a confirmed expired session); server
failures remain visible with a retryable Sign out action.

Files: `src/auth` owns state/guards/messages, `src/api/auth.ts` validates API data,
`src/pages/AuthPage.tsx` handles both forms, and `LogoutButton` is part of the shell.
Dashboard/module content and backend sources are unchanged. The backend must be
running with the documented auth endpoints and the existing Vite `/api` proxy.

Run `npm test` with Node 22 for six focused client tests using Node's built-in
runner and the existing TypeScript dependency. No additional testing dependency
was installed. Run `npm run lint` and `npm run build` for ESLint, strict TypeScript,
and bundling. These client tests mock fetch; they do not replace a browser test
of registration, login, refresh, routing and logout against the backend.
