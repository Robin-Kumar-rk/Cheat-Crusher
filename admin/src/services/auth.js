import { 
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as firebaseSignOut 
} from 'firebase/auth'
import { doc, getDoc, setDoc, serverTimestamp } from 'firebase/firestore'
import { auth, db } from './firebase'

export const signIn = async (email, password) => {
  try {
    const userCredential = await signInWithEmailAndPassword(auth, email, password)
    const user = userCredential.user
    
    // Check if user is a teacher
    const isTeacher = await checkIfTeacher(user.uid)
    if (!isTeacher) {
      await firebaseSignOut(auth)
      throw new Error('Access denied. Teacher account required.')
    }
    
    return user
  } catch (error) {
    throw error
  }
}

export const signOut = async () => {
  try {
    await firebaseSignOut(auth)
  } catch (error) {
    throw error
  }
}

export const checkIfTeacher = async (uid) => {
  try {
    const teacherDoc = await getDoc(doc(db, 'teachers', uid))
    return teacherDoc.exists()
  } catch (error) {
    console.error('Error checking teacher status:', error)
    return false
  }
}

export const signUp = async (email, password) => {
  try {
    const userCredential = await createUserWithEmailAndPassword(auth, email, password)
    const user = userCredential.user

    // Create teacher document for this user (bootstrap)
    await setDoc(
      doc(db, 'teachers', user.uid),
      {
        email: user.email,
        createdAt: serverTimestamp()
      },
      { merge: true }
    )

    return user
  } catch (error) {
    // If teacher doc creation fails, sign out to avoid partial state
    try { await firebaseSignOut(auth) } catch {}
    throw error
  }
}