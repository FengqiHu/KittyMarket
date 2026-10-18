import { mediaUrl } from './media';

export async function preloadProductCovers(paths) {
  // Keep the current cards visible until the new covers can be painted.
  // A slow or broken image must not hold up pagination indefinitely.
  let timeout;
  const covers = paths.filter(path => path && !/\.(mp4|avi|webm)$/i.test(path))
    .map(path => {
      const cover = new Image();
      cover.src = mediaUrl(path);
      return cover.decode().catch(() => {});
    });
  try {
    await Promise.race([
      Promise.all(covers),
      new Promise(resolve => { timeout = setTimeout(resolve, 1200); })
    ]);
  } finally { clearTimeout(timeout); }
}
