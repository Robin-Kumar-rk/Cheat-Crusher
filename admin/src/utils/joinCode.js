// Generate an unreadable join code from unlockPassword and start time
// Format: J1<base64url(nonce(4) + maskedEpoch(8))>-<checksum6>
// maskedEpoch = epochSec ^ first8bytes(sha256(unlockPassword))

export async function generateJoinCode(unlockPassword, startTime) {
  if (!unlockPassword || typeof unlockPassword !== 'string') {
    throw new Error('Unlock password missing')
  }
  if (!startTime || isNaN(new Date(startTime).getTime())) {
    throw new Error('Invalid start time')
  }
  const epochSec = Math.floor(new Date(startTime).getTime() / 1000)
  const encoder = new TextEncoder()
  const hashBuf = await crypto.subtle.digest('SHA-256', encoder.encode(unlockPassword))
  const hashBytes = new Uint8Array(hashBuf).slice(0, 8)
  const mask = bytesToBigInt(hashBytes)
  const masked = BigInt(epochSec) ^ mask
  const maskedBytes = bigIntToBytes(masked, 8)
  const payload = maskedBytes // deterministic payload for same inputs
  const base = base64url(payload)
  const checksum = await checksum6(payload, unlockPassword)
  return `J2${base}-${checksum}`
}

function base64url(bytes) {
  let str = btoa(String.fromCharCode(...bytes))
  return str.replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}

async function checksum6(payload, password) {
  const concat = new Uint8Array(payload.length + password.length)
  concat.set(payload, 0)
  concat.set(new TextEncoder().encode(password), payload.length)
  const digest = await crypto.subtle.digest('SHA-256', concat)
  const hex = Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('')
  return hex.substring(0, 6).toUpperCase()
}

function bytesToBigInt(bytes) {
  let result = 0n
  for (let i = 0; i < bytes.length; i++) {
    result = (result << 8n) | BigInt(bytes[i])
  }
  return result
}

function bigIntToBytes(n, length) {
  const arr = new Uint8Array(length)
  for (let i = length - 1; i >= 0; i--) {
    arr[i] = Number(n & 0xFFn)
    n >>= 8n
  }
  return arr
}
