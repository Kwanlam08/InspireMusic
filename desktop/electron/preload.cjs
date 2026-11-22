const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  // Window controls
  minimize: () => ipcRenderer.invoke('window-minimize'),
  maximize: () => ipcRenderer.invoke('window-maximize'),
  close: () => ipcRenderer.invoke('window-close'),
  isMaximized: () => ipcRenderer.invoke('window-is-maximized'),
  onMaximizedChange: (callback) => {
    const handler = (_event, value) => callback(value);
    ipcRenderer.on('window-maximized-change', handler);
    return () => ipcRenderer.removeListener('window-maximized-change', handler);
  },

  // Fullscreen controls
  setFullScreen: (flag) => ipcRenderer.invoke('window-fullscreen', flag),
  isFullScreen: () => ipcRenderer.invoke('window-is-fullscreen'),
  onFullScreenChange: (callback) => {
    const handler = (_event, value) => callback(value);
    ipcRenderer.on('window-fullscreen-change', handler);
    return () => ipcRenderer.removeListener('window-fullscreen-change', handler);
  },

  // Mini mode
  toggleMiniMode: () => ipcRenderer.invoke('toggle-mini-mode'),
  onMiniModeChange: (callback) => {
    const handler = (_event, value) => callback(value);
    ipcRenderer.on('mini-mode-change', handler);
    return () => ipcRenderer.removeListener('mini-mode-change', handler);
  },

  // Desktop floating lyrics
  toggleDesktopLyrics: () => ipcRenderer.invoke('toggle-desktop-lyrics'),
  closeDesktopLyrics: () => ipcRenderer.send('desktop-lyrics-close'),
  syncLyricsState: (data) => ipcRenderer.send('sync-lyrics-state', data),
  onLyricsStateUpdate: (callback) => {
    const handler = (_event, data) => callback(data);
    ipcRenderer.on('lyrics-state-update', handler);
    return () => ipcRenderer.removeListener('lyrics-state-update', handler);
  },
  sendLyricsControl: (action) => ipcRenderer.send('lyrics-control-action', action),
  onPlayerControl: (callback) => {
    const handler = (_event, action) => callback(action);
    ipcRenderer.on('player-control', handler);
    return () => ipcRenderer.removeListener('player-control', handler);
  },

  // File explorer
  showItemInFolder: (filePath) => ipcRenderer.invoke('show-item-in-folder', filePath),

  // Directory and library scanning
  selectDirectory: () => ipcRenderer.invoke('select-directory'),
  scanLibrary: (dirPath) => ipcRenderer.invoke('scan-library', dirPath),
  readLyricsFile: (filePath) => ipcRenderer.invoke('read-lyrics-file', filePath),
  onScanProgress: (callback) => {
    const handler = (_event, data) => callback(data);
    ipcRenderer.on('scan-progress', handler);
    return () => ipcRenderer.removeListener('scan-progress', handler);
  },
});
