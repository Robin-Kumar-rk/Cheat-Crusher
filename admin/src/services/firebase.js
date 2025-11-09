import { initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore, initializeFirestore } from 'firebase/firestore'

// Firebase configuration
// Replace with your actual Firebase config
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyD4YkIIFDXQF80UVY7fWeW7lP1aooF25V4",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "cheat-crusher.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "cheat-crusher",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "cheat-crusher.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "163159665793",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:163159665793:web:22bbae34aa260c5a467180"
}

// Initialize Firebase
const app = initializeApp(firebaseConfig)

// Initialize Firebase services
export const auth = getAuth(app)

// Initialize Firestore with settings to fix connection issues
export const db = initializeFirestore(app, {
  experimentalForceLongPolling: true, // Force long polling to avoid WebSocket issues
  cacheSizeBytes: 1048576 // 1MB cache size
})

console.log('Firestore initialized with long polling and cache settings')

export default app