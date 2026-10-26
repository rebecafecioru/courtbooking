// Where the backend is. Empty means "the same site as this page": in
// development Vite forwards /api to the backend (see vite.config.js), and in
// the Docker setup nginx does. Set VITE_API_URL when building only if the
// backend is hosted somewhere else, e.g. VITE_API_URL=https://api.example.com
export const API_URL = import.meta.env.VITE_API_URL ?? ''
