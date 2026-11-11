// Test-only: validates the actual ZIP-extracted trees with unchanged Manabi Web readers.
import assert from 'node:assert/strict'
import { readFile,readdir } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
const [frontend,artifacts]=process.argv.slice(2).map(p=>path.resolve(p))
const { validateDistribution }=await import(pathToFileURL(path.join(frontend,'scripts/content/contract.mjs')))
const { parseJson,canonicalText }=await import(pathToFileURL(path.join(frontend,'src/content/schema.mjs')))
const { validatePublishedManifest }=await import(pathToFileURL(path.join(frontend,'src/content/distributionClient.mjs')))
const { validatePackage }=await import(pathToFileURL(path.join(frontend,'src/content/installer.mjs')))
const cases=new Map(),descriptors=new Map()
for(const name of ['core','latest','historical','empty']){
 const root=path.join(artifacts,name,'educational-content')
 const bytes=await readFile(path.join(root,'manifest.json'))
 const manifest=parseJson(new Uint8Array(bytes),name)
 assert.equal(bytes.toString('utf8'),canonicalText(manifest))
 await validatePublishedManifest(manifest)
 cases.set(name,{root,manifest})
 for(const descriptor of manifest.packages)descriptors.set(descriptor.url,descriptor)
}
let validated=0
for(const [name,{root,manifest}] of cases){
 const files=new Map()
 for(const slot of await readdir(path.join(root,'packages'))){
  for(const hash of await readdir(path.join(root,'packages',slot))){
   const key='packages/'+slot+'/'+hash,descriptor=descriptors.get(key)
   assert.ok(descriptor,'Historical package has known approved-test descriptor: '+key)
   const bytes=new Uint8Array(await readFile(path.join(root,key)))
   const body=await validatePackage(descriptor,bytes)
   assert.equal(Buffer.from(bytes).toString('utf8'),canonicalText(body))
   files.set(key,bytes);validated++
  }
 }
 assert.equal(validateDistribution(manifest,files).length,name==='empty'?8:4)
 assert.equal(files.size,name==='empty'?8:name==='core'?4:5)
 if(name==='core'){
  const current=parseJson(new Uint8Array(await readFile(path.join(frontend,'public/educational-content/manifest.json'))),'current')
  assert.deepEqual(manifest,current)
 }
 console.log(JSON.stringify({scenario:name,manifestPackages:manifest.packages.length,retainedPackages:files.size,status:'PASS'}))
}
assert.deepEqual(cases.get('core').manifest,cases.get('historical').manifest,'Historical export keeps its original manifest')
assert.notEqual(cases.get('latest').manifest.releaseId,cases.get('historical').manifest.releaseId)
console.log(JSON.stringify({status:'PASS',validatedPackages:validated,frontendModified:false}))
