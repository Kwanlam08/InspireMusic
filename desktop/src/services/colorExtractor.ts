import { MeshGradientColors } from '../types';

const DEFAULT_MESH: MeshGradientColors = {
  baseBackground: '#0c0b11',
  primary: '#6b3ba7',
  secondary: '#d83a6f',
  tertiary: '#2a5bb5',
  accent: '#e87a36',
};

/**
 * Extracts 4-5 dynamic colors from an image URL using an offscreen canvas.
 */
export async function extractMeshColors(imageUrl: string | null | undefined): Promise<MeshGradientColors> {
  if (!imageUrl) return DEFAULT_MESH;

  return new Promise((resolve) => {
    const img = new Image();
    const isRemote = imageUrl.startsWith('http://') || imageUrl.startsWith('https://');
    if (isRemote) {
      img.crossOrigin = 'Anonymous';
    }
    img.src = imageUrl;

    img.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        if (!ctx) {
          resolve(DEFAULT_MESH);
          return;
        }

        const size = 32;
        canvas.width = size;
        canvas.height = size;
        ctx.drawImage(img, 0, 0, size, size);

        const imgData = ctx.getImageData(0, 0, size, size).data;
        const colorBuckets: { r: number; g: number; b: number; count: number; sat: number; lum: number }[] = [];

        // Sample pixels with quantization
        for (let i = 0; i < imgData.length; i += 16) {
          const r = imgData[i];
          const g = imgData[i + 1];
          const b = imgData[i + 2];
          const a = imgData[i + 3];

          if (a < 128) continue; // Skip transparent

          const max = Math.max(r, g, b);
          const min = Math.min(r, g, b);
          const lum = (max + min) / 510;
          const sat = max === min ? 0 : (max - min) / (1 - Math.abs(2 * lum - 1));

          colorBuckets.push({ r, g, b, count: 1, sat, lum });
        }

        if (colorBuckets.length === 0) {
          resolve(DEFAULT_MESH);
          return;
        }

        // Sort by saturation to find vibrant colors
        const sortedBySat = [...colorBuckets].sort((a, b) => b.sat - a.sat);
        // Sort by luminance for dark/light variations
        const sortedByLum = [...colorBuckets].sort((a, b) => a.lum - b.lum);

        // Pick 4 vivid, rich colors
        const pickColor = (idx: number, boost = 1.15) => {
          const c = sortedBySat[Math.min(idx, sortedBySat.length - 1)];
          const r = Math.min(255, Math.max(20, Math.floor(c.r * boost)));
          const g = Math.min(255, Math.max(20, Math.floor(c.g * boost)));
          const b = Math.min(255, Math.max(20, Math.floor(c.b * boost)));
          return `rgb(${r}, ${g}, ${b})`;
        };

        const dominant = sortedBySat[0];
        // Create rich, luminous base background tinted by the dominant color
        const baseBg = `rgb(${Math.floor(dominant.r * 0.35 + 10)}, ${Math.floor(dominant.g * 0.35 + 10)}, ${Math.floor(dominant.b * 0.4 + 14)})`;

        resolve({
          baseBackground: baseBg,
          primary: pickColor(0, 1.25),
          secondary: pickColor(Math.floor(sortedBySat.length * 0.25), 1.2),
          tertiary: pickColor(Math.floor(sortedBySat.length * 0.5), 1.15),
          accent: pickColor(Math.floor(sortedBySat.length * 0.75), 1.1),
        });
      } catch (e) {
        console.warn('Could not extract colors:', e);
        resolve(DEFAULT_MESH);
      }
    };

    img.onerror = () => {
      resolve(DEFAULT_MESH);
    };
  });
}
