const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(`${__dirname}/../maoliang-qianduan/src/components/BuyerShowRecommend.vue`, 'utf8')
  .split('<script>')[1].split('</script>')[0].replace(/^import .*;\n/gm, '')
  .replace('export default', 'globalThis.component =');
const storage = new Map();
let calls = [];
let fail = false;
const cats = [{ catid: 1, catname: '小灰', catage: 3 }, { catid: 2, catname: '小白', catage: 2 }];
const axios = { get: async (url, config) => {
  calls.push({ url, config });
  if (url === '/now-usr') return { data: { userid: 9 } };
  if (url === '/cat/show-cat') return { data: { page: 'success', data: cats } };
  if (fail) throw Error('离线');
  return { data: { page: 'success', data: { items: [{ goodid: 100 + config.params.batch }] } } };
} };
const context = { axios, sessionStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, String(value)) }, Date };
vm.createContext(context);
vm.runInContext(source, context);
function mount() {
  const component = context.component;
  const instance = { ...component.data(), $route: { query: {} } };
  for (const [key, value] of Object.entries(component.methods)) instance[key] = value.bind(instance);
  return instance;
}
test('returning restores the chosen cat, latest batch and cards without requesting recommendation', async () => {
  storage.clear(); calls = [];
  const page = mount();
  await page.loadCats();
  page.selectedCatId = 2;
  await page.showRecommendedCatFood();
  await page.refreshRecommendedCatFood();
  assert.equal(page.batch, 1);
  assert.equal(page.result.items[0].goodid, 101);
  calls = [];
  const returning = mount();
  await returning.loadCats();
  assert.equal(returning.selectedCatId, 2);
  assert.equal(returning.batch, 1);
  assert.equal(returning.result.items[0].goodid, 101);
  assert.equal(calls.filter(call => call.url.startsWith('/cat/recommend/')).length, 0);
});
test('failed refresh retains the previous batch and cache, retry requests the same next batch', async () => {
  const page = mount(); await page.loadCats();
  const batch = page.batch;
  fail = true; await page.refreshRecommendedCatFood(); fail = false;
  assert.equal(page.batch, batch);
  assert.equal(page.result.items[0].goodid, 100 + batch);
  await page.refreshRecommendedCatFood();
  assert.equal(page.batch, batch + 1);
});
test('profile changes, expired/corrupt cache and another user require a new result', async () => {
  const page = mount(); await page.loadCats();
  cats[1].catage += 1;
  assert.equal(page.restoreCache(), false);
  await page.showRecommendedCatFood();
  const key = page.cacheKey();
  const cache = JSON.parse(storage.get(key)); cache.savedAt = Date.now() - 31 * 60 * 1000;
  storage.set(key, JSON.stringify(cache)); assert.equal(page.restoreCache(), false);
  storage.set(key, '{'); assert.equal(page.restoreCache(), false);
  page.userId = 10; assert.equal(page.restoreCache(), false);
});
