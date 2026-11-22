const { app, BrowserWindow, ipcMain, dialog, protocol, net, shell, screen } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

// Set Application User Model ID for Windows Taskbar grouping and official icon display
app.setAppUserModelId('com.inspiremusic.desktop');

let mainWindow = null;

// Dynamically import music-metadata if needed
let mm = null;
async function getMusicMetadata() {
  if (!mm) {
    mm = await import('music-metadata');
  }
  return mm;
}

function getCoversDir() {
  const dir = path.join(app.getPath('userData'), 'covers');
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  return dir;
}

function toFileUrl(filePath) {
  if (!filePath) return '';
  const normalized = filePath.replace(/\\/g, '/');
  const parts = normalized.split('/');
  const encodedParts = parts.map((part, index) => {
    if (index === 0 && /^[a-zA-Z]:$/.test(part)) return part;
    return encodeURIComponent(part);
  });
  return 'file:///' + encodedParts.join('/').replace(/^\/+/, '');
}

function createWindow() {
  const iconIco = path.join(__dirname, 'icon.ico');
  const iconPng = path.join(__dirname, 'icon.png');
  const appIcon = fs.existsSync(iconIco) ? iconIco : iconPng;

  mainWindow = new BrowserWindow({
    width: 1200,
    height: 820,
    minWidth: 900,
    minHeight: 640,
    frame: false,
    icon: appIcon,
    backgroundColor: '#0c0c10',
    titleBarStyle: 'hidden',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false, // Allows streaming local audio files
    },
  });

  // Load Vite dev server or production index.html
  const distPath = path.join(__dirname, '../dist/index.html');
  const isDev = !app.isPackaged && process.env.NODE_ENV === 'development';

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173').catch(() => {
      if (fs.existsSync(distPath)) {
        mainWindow?.loadFile(distPath);
      }
    });
  } else {
    if (fs.existsSync(distPath)) {
      mainWindow.loadFile(distPath);
    } else {
      mainWindow.loadURL('http://localhost:5173');
    }
  }

  mainWindow.on('maximize', () => mainWindow?.webContents.send('window-maximized-change', true));
  mainWindow.on('unmaximize', () => mainWindow?.webContents.send('window-maximized-change', false));
  mainWindow.on('enter-full-screen', () => mainWindow?.webContents.send('window-fullscreen-change', true));
  mainWindow.on('leave-full-screen', () => mainWindow?.webContents.send('window-fullscreen-change', false));
}

let lyricsWindow = null;

function createLyricsWindow() {
  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;
  const winWidth = 760;
  const winHeight = 110;

  lyricsWindow = new BrowserWindow({
    width: winWidth,
    height: winHeight,
    x: Math.round((width - winWidth) / 2),
    y: height - winHeight - 20,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    resizable: true,
    hasShadow: false,
    backgroundColor: '#00000000',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false,
    },
  });

  const distPath = path.join(__dirname, '../dist/index.html');
  const isDev = !app.isPackaged && process.env.NODE_ENV === 'development';

  if (isDev) {
    lyricsWindow.loadURL('http://localhost:5173/?mode=desktop-lyrics').catch(() => {
      if (fs.existsSync(distPath)) {
        lyricsWindow?.loadFile(distPath, { query: { mode: 'desktop-lyrics' } });
      }
    });
  } else {
    if (fs.existsSync(distPath)) {
      lyricsWindow.loadFile(distPath, { query: { mode: 'desktop-lyrics' } });
    } else {
      lyricsWindow.loadURL('http://localhost:5173/?mode=desktop-lyrics');
    }
  }

  lyricsWindow.on('closed', () => {
    lyricsWindow = null;
    mainWindow?.webContents.send('desktop-lyrics-state-change', false);
  });
}

// Window controls IPC
ipcMain.handle('window-minimize', () => {
  mainWindow?.minimize();
});

ipcMain.handle('window-maximize', () => {
  if (!mainWindow) return false;
  if (mainWindow.isMaximized()) {
    mainWindow.unmaximize();
    return false;
  } else {
    mainWindow.maximize();
    return true;
  }
});

ipcMain.handle('window-close', () => {
  mainWindow?.close();
});

ipcMain.handle('window-is-maximized', () => {
  return mainWindow?.isMaximized() || false;
});

// Fullscreen toggle
ipcMain.handle('window-fullscreen', (event, flag) => {
  if (!mainWindow) return false;
  if (typeof flag === 'boolean') {
    mainWindow.setFullScreen(flag);
  } else {
    mainWindow.setFullScreen(!mainWindow.isFullScreen());
  }
  return mainWindow.isFullScreen();
});

ipcMain.handle('window-is-fullscreen', () => {
  return mainWindow?.isFullScreen() || false;
});

// Mini mode toggle
let normalBounds = { width: 1200, height: 820 };
let isMiniMode = false;

ipcMain.handle('toggle-mini-mode', () => {
  if (!mainWindow) return false;
  if (!isMiniMode) {
    normalBounds = mainWindow.getBounds();
    mainWindow.setMinimumSize(380, 130);
    mainWindow.setSize(380, 130);
    mainWindow.setAlwaysOnTop(true);
    isMiniMode = true;
    mainWindow.webContents.send('mini-mode-change', true);
    return true;
  } else {
    mainWindow.setMinimumSize(900, 640);
    mainWindow.setBounds(normalBounds);
    mainWindow.setAlwaysOnTop(false);
    isMiniMode = false;
    mainWindow.webContents.send('mini-mode-change', false);
    return false;
  }
});

// Desktop floating lyrics toggle & synchronization
ipcMain.handle('toggle-desktop-lyrics', () => {
  if (lyricsWindow && !lyricsWindow.isDestroyed()) {
    if (lyricsWindow.isVisible()) {
      lyricsWindow.hide();
      return false;
    } else {
      lyricsWindow.show();
      return true;
    }
  } else {
    createLyricsWindow();
    return true;
  }
});

ipcMain.on('desktop-lyrics-close', () => {
  if (lyricsWindow && !lyricsWindow.isDestroyed()) {
    lyricsWindow.close();
  }
});

ipcMain.on('sync-lyrics-state', (event, data) => {
  if (lyricsWindow && !lyricsWindow.isDestroyed() && lyricsWindow.isVisible()) {
    lyricsWindow.webContents.send('lyrics-state-update', data);
  }
});

ipcMain.on('lyrics-control-action', (event, action) => {
  mainWindow?.webContents.send('player-control', action);
});

// Show item in Windows File Explorer
ipcMain.handle('show-item-in-folder', (event, filePath) => {
  if (filePath && fs.existsSync(filePath)) {
    shell.showItemInFolder(filePath);
    return true;
  }
  return false;
});

// Pick folder
ipcMain.handle('select-directory', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory'],
    title: '选择本地音乐文件夹',
  });
  if (result.canceled || result.filePaths.length === 0) {
    return null;
  }
  return result.filePaths[0];
});

// Supported audio extensions
const AUDIO_EXTS = new Set(['.mp3', '.flac', '.wav', '.m4a', '.ogg', '.aac', '.alac', '.wma']);

// Recursive scanner
async function scanFolder(dirPath, maxFiles = 2000) {
  const musicFiles = [];
  const entries = await fs.promises.readdir(dirPath, { withFileTypes: true });

  for (const entry of entries) {
    if (musicFiles.length >= maxFiles) break;
    const fullPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      if (!entry.name.startsWith('.')) {
        try {
          const subFiles = await scanFolder(fullPath, maxFiles - musicFiles.length);
          musicFiles.push(...subFiles);
        } catch (err) {
          console.error('Failed to read subfolder:', fullPath, err);
        }
      }
    } else if (entry.isFile()) {
      const ext = path.extname(entry.name).toLowerCase();
      if (AUDIO_EXTS.has(ext)) {
        musicFiles.push(fullPath);
      }
    }
  }
  return musicFiles;
}

// Scan library & parse metadata
ipcMain.handle('scan-library', async (event, dirPath) => {
  try {
    const musicMetadata = await getMusicMetadata();
    const filePaths = await scanFolder(dirPath, 2000);
    const results = [];
    const coversDir = getCoversDir();

    for (let i = 0; i < filePaths.length; i++) {
      const filePath = filePaths[i];
      try {
        const metadata = await musicMetadata.parseFile(filePath, { skipCovers: false, duration: true });
        const { common, format } = metadata;
        
        let coverUrl = null;
        if (common.picture && common.picture.length > 0) {
          try {
            const pic = common.picture[0];
            const ext = pic.format && pic.format.includes('png') ? 'png' : 'jpg';
            const albumKey = `${common.album || 'album'}_${common.artist || 'artist'}`;
            const hash = crypto.createHash('md5').update(albumKey).digest('hex');
            const diskPath = path.join(coversDir, `${hash}.${ext}`);
            
            if (!fs.existsSync(diskPath)) {
              await fs.promises.writeFile(diskPath, pic.data);
            }
            coverUrl = toFileUrl(diskPath);
          } catch (covErr) {
            console.warn('Cover extraction failed for', filePath, covErr);
          }
        }

        // Try reading external .lrc file in the same directory
        let externalLyrics = null;
        const lrcPath = filePath.replace(/\.[^/.]+$/, '.lrc');
        if (fs.existsSync(lrcPath)) {
          try {
            externalLyrics = await fs.promises.readFile(lrcPath, 'utf-8');
          } catch (e) {
            // ignore
          }
        }

        const fileName = path.basename(filePath, path.extname(filePath));
        const item = {
          id: `song_${Buffer.from(filePath).toString('base64').slice(0, 20)}_${i}`,
          path: filePath,
          url: toFileUrl(filePath),
          title: common.title?.trim() || fileName,
          artist: common.artist?.trim() || '未知艺人',
          album: common.album?.trim() || '未知专辑',
          albumArtist: common.albumartist?.trim() || common.artist?.trim() || '未知艺人',
          duration: format.duration || 0,
          year: common.year || null,
          genre: common.genre ? common.genre[0] : null,
          coverUrl: coverUrl,
          lyrics: externalLyrics || (common.lyrics ? common.lyrics.join('\n') : null),
        };
        results.push(item);

        // Progress feedback every 10 songs
        if (i % 10 === 0 || i === filePaths.length - 1) {
          mainWindow?.webContents.send('scan-progress', {
            current: i + 1,
            total: filePaths.length,
          });
        }
      } catch (err) {
        // Fallback with basic file info if tag parsing fails
        const fileName = path.basename(filePath, path.extname(filePath));
        results.push({
          id: `song_${Buffer.from(filePath).toString('base64').slice(0, 20)}_${i}`,
          path: filePath,
          url: toFileUrl(filePath),
          title: fileName,
          artist: '未知艺人',
          album: '本地音乐',
          albumArtist: '未知艺人',
          duration: 180,
          year: null,
          genre: null,
          coverUrl: null,
          lyrics: null,
        });
      }
    }
    return { success: true, songs: results };
  } catch (error) {
    console.error('Scan failed:', error);
    return { success: false, error: error.message, songs: [] };
  }
});

// Read lyrics file
ipcMain.handle('read-lyrics-file', async (event, filePath) => {
  const lrcPath = filePath.replace(/\.[^/.]+$/, '.lrc');
  if (fs.existsSync(lrcPath)) {
    try {
      return await fs.promises.readFile(lrcPath, 'utf-8');
    } catch (e) {
      return null;
    }
  }
  return null;
});

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
