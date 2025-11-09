import { collection, getDocs, query, where } from 'firebase/firestore'
import { db } from '../services/firebase'

// Debug function to examine current responses
export const debugCurrentResponses = async () => {
  try {
    console.log('=== RESPONSE DEBUG START ===')
    
    // Get all responses
    const allResponsesQuery = collection(db, 'responses')
    const allResponsesSnapshot = await getDocs(allResponsesQuery)
    
    console.log('Total responses in database:', allResponsesSnapshot.size)
    
    if (allResponsesSnapshot.size > 0) {
      console.log('All responses details:')
      allResponsesSnapshot.forEach((doc, index) => {
        const data = doc.data()
        console.log(`Response ${index + 1}:`, {
          id: doc.id,
          quizId: data.quizId,
          userId: data.userId,
          userDeviceId: data.userDeviceId,
          rollNumber: data.rollNumber,
          clientSubmittedAt: data.clientSubmittedAt,
          serverUploadedAt: data.serverUploadedAt,
          score: data.score,
          gradeStatus: data.gradeStatus,
          flagged: data.flagged,
          disqualified: data.disqualified,
          appSwitchEvents: data.appSwitchEvents,
          answers: data.answers ? Object.keys(data.answers).length + ' answers' : 'no answers'
        })
        
        // Show app switch events in detail
        if (data.appSwitchEvents && data.appSwitchEvents.length > 0) {
          console.log(`  App Switch Events for Response ${index + 1}:`, data.appSwitchEvents)
        } else {
          console.log(`  No app switch events for Response ${index + 1}`)
        }
      })
    }
    
    console.log('=== RESPONSE DEBUG END ===')
    
  } catch (error) {
    console.error('Debug responses error:', error)
  }
}

// Function to run the debug from browser console
window.debugResponses = debugCurrentResponses