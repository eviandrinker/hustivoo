import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

// Exercise the actual client with Node's built-in runner; no extra test packages.
const source = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2023, module: ts.ModuleKind.ESNext } }).outputText
const client = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const token = { headerName: 'X-CSRF-TOKEN', token: 'test-token' }

 test('mutations bootstrap CSRF and send same-origin credentials, including 204 logout', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    return path.endsWith('/csrf') ? json(token) : new Response(null, { status: 204 })
  })
  assert.equal(await client.requestJson('/api/v1/auth/logout', { method: 'POST' }), undefined)
  assert.deepEqual(calls.map(call => call.path), ['/api/v1/auth/csrf', '/api/v1/auth/logout'])
  assert.equal(calls[1].options.headers.get('X-CSRF-TOKEN'), token.token)
  assert.equal(calls[1].options.credentials, 'same-origin')
})

test('401 expires authentication except for invalid login credentials', async t => {
  let expired = 0
  const unsubscribe = client.subscribeUnauthorized(() => expired++)
  t.after(unsubscribe)
  t.mock.method(globalThis, 'fetch', async path => path.endsWith('/csrf') ? json(token) : json({}, 401))
  await assert.rejects(client.requestJson('/api/v1/auth/login', { method: 'POST' }), error => error.status === 401)
  assert.equal(expired, 0)
  await assert.rejects(client.requestJson('/api/v1/auth/me'), error => error.status === 401)
  assert.equal(expired, 1)
})

test('403 checks for session expiry without retrying a mutation', async t => {
  const paths = []
  let expired = false
  t.after(client.subscribeUnauthorized(() => { expired = true }))
  t.mock.method(globalThis, 'fetch', async path => {
    paths.push(path)
    return path.endsWith('/csrf') ? json(token) : json({}, path.endsWith('/me') ? 401 : 403)
  })
  await assert.rejects(client.requestJson('/api/v1/projects', { method: 'POST' }), error => error.status === 403)
  assert.deepEqual(paths, ['/api/v1/auth/csrf', '/api/v1/projects', '/api/v1/auth/me'])
  assert.equal(expired, true)
})

test('malformed CSRF responses prevent mutations', async t => {
  const paths = []
  t.mock.method(globalThis, 'fetch', async path => { paths.push(path); return json({ token: 'missing-header' }) })
  await assert.rejects(client.requestJson('/api/v1/auth/register', { method: 'POST' }))
  assert.deepEqual(paths, ['/api/v1/auth/csrf'])
})

test('rejects unexpected response types and external destinations', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('<html/>', { headers: { 'Content-Type': 'text/html' } }))
  await assert.rejects(client.requestJson('/api/v1/auth/me'))
  await assert.rejects(client.requestJson('https://example.com/api/secret'))
})

test('network failures remain failures and do not invalidate a session', async t => {
  let expired = false
  t.after(client.subscribeUnauthorized(() => { expired = true }))
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('offline') })
  await assert.rejects(client.requestJson('/api/v1/auth/me'))
  assert.equal(expired, false)
})
