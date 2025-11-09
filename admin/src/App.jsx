import React, { useState, useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Box, CircularProgress } from '@mui/material'
import { onAuthStateChanged } from 'firebase/auth'
import { auth } from './services/firebase'
import { checkIfTeacher } from './services/auth'

// Pages
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import DashboardPage from './pages/DashboardPage'
import CreateQuizPage from './pages/CreateQuizPage'
import EditQuizPage from './pages/EditQuizPage'
import QuizDetailsPage from './pages/QuizDetailsPage'
import ResponsesPage from './pages/ResponsesPage'

// Components
import Layout from './components/Layout'

function App() {
  const [user, setUser] = useState(null)
  const [isTeacher, setIsTeacher] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (user) => {
      if (user) {
        // Check if user is a teacher
        const teacherStatus = await checkIfTeacher(user.uid)
        setUser(user)
        setIsTeacher(teacherStatus)
      } else {
        setUser(null)
        setIsTeacher(false)
      }
      setLoading(false)
    })

    return () => unsubscribe()
  }, [])

  if (loading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
      >
        <CircularProgress />
      </Box>
    )
  }

  if (!user || !isTeacher) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Layout user={user}>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/create-quiz" element={<CreateQuizPage />} />
        <Route path="/quiz/:quizId/edit" element={<EditQuizPage />} />
        <Route path="/quiz/:quizId/responses" element={<ResponsesPage />} />
        <Route path="/quiz/:quizId" element={<QuizDetailsPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Layout>
  )
}

export default App