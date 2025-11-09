import { 
  collection, 
  doc, 
  addDoc, 
  getDoc, 
  getDocs, 
  updateDoc, 
  deleteDoc, 
  query, 
  where, 
  orderBy,
  serverTimestamp 
} from 'firebase/firestore'
import { db, auth } from './firebase'

// Generate random 6-character quiz code
const generateQuizCode = () => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  let result = ''
  for (let i = 0; i < 6; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

// Check if quiz code is unique
const isCodeUnique = async (code) => {
  const q = query(collection(db, 'quizzes'), where('code', '==', code))
  const querySnapshot = await getDocs(q)
  return querySnapshot.empty
}

// Generate unique quiz code
const generateUniqueCode = async () => {
  let code
  let isUnique = false
  
  while (!isUnique) {
    code = generateQuizCode()
    isUnique = await isCodeUnique(code)
  }
  
  return code
}

export const createQuiz = async (quizData, creatorId) => {
  try {
    const code = await generateUniqueCode()
    
    const quiz = {
      ...quizData,
      code,
      creatorId,
      createdAt: serverTimestamp(),
      editableUntilStart: true
    }
    
    const docRef = await addDoc(collection(db, 'quizzes'), quiz)
    return { id: docRef.id, ...quiz }
  } catch (error) {
    throw error
  }
}

export const getQuiz = async (quizId) => {
  try {
    console.log('getQuiz called with ID:', quizId)
    const docRef = doc(db, 'quizzes', quizId)
    const docSnap = await getDoc(docRef)
    
    if (docSnap.exists()) {
      const quiz = { id: docSnap.id, ...docSnap.data() }
      console.log('Quiz found:', quiz)
      return quiz
    } else {
      console.log('Quiz not found for ID:', quizId)
      throw new Error('Quiz not found')
    }
  } catch (error) {
    console.error('Error in getQuiz:', error)
    throw error
  }
}

export const getQuizzes = async (creatorId) => {
  try {
    // First try with orderBy, if it fails, fall back to simple query
    let q
    try {
      q = query(
        collection(db, 'quizzes'),
        where('creatorId', '==', creatorId),
        orderBy('createdAt', 'desc')
      )
    } catch (indexError) {
      // Fallback to simple query without orderBy if index doesn't exist
      q = query(
        collection(db, 'quizzes'),
        where('creatorId', '==', creatorId)
      )
    }
    
    const querySnapshot = await getDocs(q)
    const quizzes = []
    
    querySnapshot.forEach((doc) => {
      quizzes.push({ id: doc.id, ...doc.data() })
    })
    
    // Sort manually if we couldn't use orderBy
    quizzes.sort((a, b) => {
      if (!a.createdAt || !b.createdAt) return 0
      return b.createdAt.seconds - a.createdAt.seconds
    })
    
    return quizzes
  } catch (error) {
    throw error
  }
}

export const updateQuiz = async (quizId, updates) => {
  try {
    const docRef = doc(db, 'quizzes', quizId)
    // Prevent editing after start
    const current = await getDoc(docRef)
    if (current.exists()) {
      const data = current.data()
      const startsAt = data.startsAt?.toDate ? data.startsAt.toDate() : new Date(data.startsAt)
      if (startsAt && new Date() >= startsAt) {
        throw new Error('Quiz has started and cannot be edited. Please clone the quiz to make changes.')
      }
    }
    await updateDoc(docRef, updates)
    return true
  } catch (error) {
    throw error
  }
}

export const deleteQuiz = async (quizId) => {
  try {
    const docRef = doc(db, 'quizzes', quizId)
    await deleteDoc(docRef)
    return true
  } catch (error) {
    throw error
  }
}

export const getQuizResponses = async (quizId) => {
  try {
    const q = query(
      collection(db, 'responses'),
      where('quizId', '==', quizId),
      orderBy('clientSubmittedAt', 'desc')
    )
    
    const querySnapshot = await getDocs(q)
    const responses = []
    
    querySnapshot.forEach((doc) => {
      responses.push({
        id: doc.id,
        ...doc.data()
      })
    })
    
    // Handle responses with null clientSubmittedAt by sorting them to the end
    responses.sort((a, b) => {
      // If both have timestamps, sort by timestamp (desc)
      if (a.clientSubmittedAt && b.clientSubmittedAt) {
        const aTime = a.clientSubmittedAt.toDate()
        const bTime = b.clientSubmittedAt.toDate()
        return bTime - aTime
      }
      
      // If only one has timestamp, prioritize the one with timestamp
      if (a.clientSubmittedAt && !b.clientSubmittedAt) return -1
      if (!a.clientSubmittedAt && b.clientSubmittedAt) return 1
      
      // If both are null, maintain original order
      return 0
    })
    
    return responses
    
  } catch (error) {
    console.error('Error getting quiz responses:', error)
    
    // Fallback: query without orderBy if index is missing
    try {
      const fallbackQuery = query(
        collection(db, 'responses'),
        where('quizId', '==', quizId)
      )
      
      const querySnapshot = await getDocs(fallbackQuery)
      const responses = []
      
      querySnapshot.forEach((doc) => {
        responses.push({
          id: doc.id,
          ...doc.data()
        })
      })
      
      // Sort manually with null handling
      responses.sort((a, b) => {
        if (a.clientSubmittedAt && b.clientSubmittedAt) {
          const aTime = a.clientSubmittedAt.toDate()
          const bTime = b.clientSubmittedAt.toDate()
          return bTime - aTime
        }
        if (a.clientSubmittedAt && !b.clientSubmittedAt) return -1
        if (!a.clientSubmittedAt && b.clientSubmittedAt) return 1
        return 0
      })
      
      return responses
      
    } catch (fallbackError) {
      console.error('Fallback query also failed:', fallbackError)
      throw fallbackError
    }
  }
}

export const cloneQuiz = async (sourceQuizId, overrides = {}) => {
  try {
    const srcRef = doc(db, 'quizzes', sourceQuizId)
    const srcSnap = await getDoc(srcRef)
    if (!srcSnap.exists()) throw new Error('Source quiz not found')
    const src = srcSnap.data()

    const newCode = await generateUniqueCode()
    const cloned = {
      title: src.title,
      code: newCode,
      // Clone under the current teacher by default; fallback to source creator
      creatorId: auth.currentUser?.uid || src.creatorId,
      createdAt: serverTimestamp(),
      startsAt: overrides.startsAt ?? src.startsAt,
      endsAt: overrides.endsAt ?? src.endsAt,
      allowLateUploadSec: overrides.allowLateUploadSec ?? src.allowLateUploadSec ?? 0,
      allowJoinAfterStart: overrides.allowJoinAfterStart ?? src.allowJoinAfterStart ?? false,
      maxLateSec: overrides.maxLateSec ?? src.maxLateSec ?? 0,
      onAppSwitch: overrides.onAppSwitch ?? src.onAppSwitch ?? 'flag',
      showResultsAt: overrides.showResultsAt ?? src.showResultsAt,
      showAnswersWithMarks: overrides.showAnswersWithMarks ?? src.showAnswersWithMarks ?? false,
      shuffleQuestions: overrides.shuffleQuestions ?? src.shuffleQuestions ?? true,
      shuffleOptions: overrides.shuffleOptions ?? src.shuffleOptions ?? true,
      autoDeleteAfterDays: overrides.autoDeleteAfterDays ?? src.autoDeleteAfterDays ?? 7,
      editableUntilStart: true,
      preJoinFields: overrides.preJoinFields ?? src.preJoinFields ?? [],
      questions: src.questions ?? []
    }

    const newRef = await addDoc(collection(db, 'quizzes'), cloned)
    return { id: newRef.id, ...cloned }
  } catch (error) {
    throw error
  }
}

export const updateResponseGrade = async (responseId, score, gradeStatus = 'graded') => {
  try {
    const docRef = doc(db, 'responses', responseId)
    await updateDoc(docRef, {
      score,
      gradeStatus
    })
    return true
  } catch (error) {
    throw error
  }
}

export const unbindUserDevice = async (userId) => {
  try {
    const docRef = doc(db, 'users', userId)
    await updateDoc(docRef, {
      deviceId: ''
    })
    return true
  } catch (error) {
    throw error
  }
}