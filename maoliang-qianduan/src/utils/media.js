const assets = require.context('../assets/img', true, /buyer\/.*\.(png|jpe?g|gif|webp)$/i);
export function mediaUrl(path) {
  const value = String(path || '').split(',')[0].trim();
  if (/^(https?:|data:|blob:)/i.test(value)) return value;
  if (/^\/demo\/(?:[a-z-]+\.svg|products\/[a-z-]+\.webp)$/.test(value)) return value;
  if (/^\/catalog\/images\/(?:petsperfect|petocart)_\d+\.(?:jpe?g|png|webp|gif)$/.test(value)) return `/api${value}`;
  const asset = value.replace(/^\.\//, '').replace(/^\/?assets\/img\//, './');
  if (assets.keys().includes(asset)) return assets(asset);
  if (value.startsWith('./img/') || value.startsWith('/img/')) return `/api/img/${value.split('/').pop()}`;
  return assets('./buyer/food-1.jpg');
}
