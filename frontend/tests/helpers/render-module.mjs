import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { pathToFileURL, fileURLToPath } from 'node:url'
import path from 'node:path'
import ts from 'typescript'

// Render real TSX components with the installed React server renderer; no DOM dependency.
const require = createRequire(import.meta.url)
const root = fileURLToPath(new URL('../../src/', import.meta.url))
const cache = new Map()
export async function renderModule(file) {
  const resolved = path.resolve(root, file)
  if (cache.has(resolved)) return cache.get(resolved)
  let source
  let filename
  for (const extension of ['', '.ts', '.tsx']) {
    try { filename = resolved + extension; source = await readFile(filename, 'utf8'); break } catch (error) { if (error.code !== 'ENOENT') throw error }
  }
  if (!source) throw new Error(`Module not found: ${resolved}`)
  let compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2023, module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.ReactJSX } }).outputText
  for (const match of [...compiled.matchAll(/from\s+(['"])([^'"]+)\1/g)]) {
    const specifier = match[2]
    const url = specifier.startsWith('@/') ? await renderModule(specifier.slice(2))
      : specifier.startsWith('.') ? await renderModule(path.resolve(path.dirname(filename), specifier))
      : pathToFileURL(require.resolve(specifier)).href
    compiled = compiled.replace(match[0], `from ${JSON.stringify(url)}`)
  }
  const url = `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
  cache.set(resolved, url)
  return url
}
