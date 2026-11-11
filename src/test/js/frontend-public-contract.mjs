// Isolated compatibility verification. Reads existing Web validators; never edits Web or publishes files.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const [frontend, generated] = process.argv.slice(2).map(p => path.resolve(p))
assert.ok(frontend && generated)
const { validateDistribution } = await import(pathToFileURL(path.join(frontend, 'scripts/content/contract.mjs')))
const { canonicalText, parseJson } = await import(pathToFileURL(path.join(frontend, 'src/content/schema.mjs')))
const { validatePackage } = await import(pathToFileURL(path.join(frontend, 'src/content/installer.mjs')))
const { validatePublishedManifest } = await import(pathToFileURL(path.join(frontend, 'src/content/distributionClient.mjs')))
const sourceManifest = parseJson(new Uint8Array(await readFile(path.join(frontend, 'public/educational-content/manifest.json'))), 'existing manifest')
let packages = 0
for (const name of ['core', 'empty', 'technical']) {
  const root = path.join(generated, name)
  const bytes = await readFile(path.join(root, 'manifest.json'))
  const manifest = parseJson(new Uint8Array(bytes), name)
  assert.equal(bytes.toString('utf8'), canonicalText(manifest), 'Java canonical manifest')
  await validatePublishedManifest(manifest)
  const files = new Map()
  for (const d of manifest.packages) {
    const bytes = new Uint8Array(await readFile(path.join(root, ...d.url.split('/'))))
    const body = await validatePackage(d, bytes)
    assert.equal(Buffer.from(bytes).toString('utf8'), canonicalText(body), d.packageId)
    files.set(d.url, bytes); packages++
  }
  const summary = validateDistribution(manifest, files, { allowTechnicalFixtures: name === 'technical' })
  assert.equal(summary.length, name === 'core' ? 4 : 8)
  if (name === 'core') {
    assert.deepEqual(manifest, sourceManifest, 'No-op Java export matches existing release and all descriptors')
    for (const d of manifest.packages) assert.deepEqual(Buffer.from(files.get(d.url)),
      await readFile(path.join(frontend, 'public/educational-content', ...d.url.split('/'))))
  }
  if (name === 'technical') assert.throws(() => validateDistribution(manifest, files), /fixtures cannot be published/)
  if (name === 'empty') {
    const fixed = manifest.packages.find(p => p.packageId === 'phrases-fixed')
    const pattern = manifest.packages.find(p => p.packageId === 'phrases-pattern')
    assert.equal(fixed.sha256, pattern.sha256); assert.notEqual(fixed.url, pattern.url)
  }
  console.log(JSON.stringify({ scenario: name, packages: summary.length, releaseId: manifest.releaseId, status: 'PASS' }))
}
console.log(JSON.stringify({ status: 'PASS', validatedJavaPackages: packages, frontendModified: false }))
