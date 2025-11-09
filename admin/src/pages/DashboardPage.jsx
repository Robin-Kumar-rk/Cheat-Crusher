import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Typography,
  Button,
  Card,
  CardContent,
  CardActions,
  Grid,
  Chip,
  Alert,
  CircularProgress,
  IconButton,
  Menu,
  MenuItem
} from '@mui/material'
import {
  Add as AddIcon,
  MoreVert as MoreVertIcon,
  People as PeopleIcon,
  Quiz as QuizIcon,
  Schedule as ScheduleIcon,
  Warning as WarningIcon
} from '@mui/icons-material'
import { getQuizzes, deleteQuiz } from '../services/quizService'
import { auth } from '../services/firebase'
import { format } from 'date-fns'

function DashboardPage() {
  const navigate = useNavigate()
  const [quizzes, setQuizzes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [anchorEl, setAnchorEl] = useState(null)
  const [selectedQuiz, setSelectedQuiz] = useState(null)

  useEffect(() => {
    loadQuizzes()
  }, [])

  const loadQuizzes = async () => {
    try {
      setLoading(true)
      const userQuizzes = await getQuizzes(auth.currentUser.uid)
      setQuizzes(userQuizzes)
    } catch (error) {
      setError('Failed to load quizzes: ' + error.message)
    } finally {
      setLoading(false)
    }
  }

  const handleMenuOpen = (event, quiz) => {
    setAnchorEl(event.currentTarget)
    setSelectedQuiz(quiz)
  }

  const handleMenuClose = () => {
    setAnchorEl(null)
    setSelectedQuiz(null)
  }

  const handleDeleteQuiz = async () => {
    if (!selectedQuiz) return

    try {
      await deleteQuiz(selectedQuiz.id)
      setQuizzes(quizzes.filter(q => q.id !== selectedQuiz.id))
      handleMenuClose()
    } catch (error) {
      setError('Failed to delete quiz: ' + error.message)
    }
  }

  const getQuizStatus = (quiz) => {
    const now = new Date()
    const startTime = quiz.startsAt?.toDate()
    const endTime = quiz.endsAt?.toDate()

    if (!startTime || !endTime) return { status: 'draft', color: 'default' }

    if (now < startTime) {
      return { status: 'scheduled', color: 'info' }
    } else if (now >= startTime && now <= endTime) {
      return { status: 'active', color: 'success' }
    } else {
      return { status: 'completed', color: 'default' }
    }
  }

  const shouldShowDataRetentionWarning = (quiz) => {
    if (!quiz.endsAt) return false
    
    const endTime = quiz.endsAt.toDate()
    const deleteTime = new Date(endTime.getTime() + (quiz.autoDeleteAfterDays || 7) * 24 * 60 * 60 * 1000)
    const now = new Date()
    const daysUntilDeletion = Math.ceil((deleteTime - now) / (24 * 60 * 60 * 1000))
    
    return daysUntilDeletion <= 3 && daysUntilDeletion > 0
  }

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Dashboard
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate('/create-quiz')}
        >
          Create Quiz
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {quizzes.length === 0 ? (
        <Card>
          <CardContent sx={{ textAlign: 'center', py: 6 }}>
            <QuizIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              No quizzes yet
            </Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              Create your first quiz to get started
            </Typography>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => navigate('/create-quiz')}
            >
              Create Quiz
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Grid container spacing={3}>
          {quizzes.map((quiz) => {
            const status = getQuizStatus(quiz)
            const showWarning = shouldShowDataRetentionWarning(quiz)

            return (
              <Grid item xs={12} md={6} lg={4} key={quiz.id}>
                <Card>
                  <CardContent>
                    <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
                      <Typography variant="h6" component="h2" noWrap>
                        {quiz.title}
                      </Typography>
                      <IconButton
                        size="small"
                        onClick={(e) => handleMenuOpen(e, quiz)}
                      >
                        <MoreVertIcon />
                      </IconButton>
                    </Box>

                    <Box display="flex" gap={1} mb={2}>
                      <Chip
                        label={status.status.toUpperCase()}
                        color={status.color}
                        size="small"
                      />
                      <Chip
                        label={quiz.code}
                        variant="outlined"
                        size="small"
                      />
                    </Box>

                    {showWarning && (
                      <Alert severity="warning" sx={{ mb: 2 }} icon={<WarningIcon />}>
                        <Typography variant="body2">
                          Responses will be auto-deleted soon. Export now!
                        </Typography>
                      </Alert>
                    )}

                    <Box display="flex" alignItems="center" gap={1} mb={1}>
                      <QuizIcon fontSize="small" color="action" />
                      <Typography variant="body2" color="text.secondary">
                        {quiz.questions?.length || 0} questions
                      </Typography>
                    </Box>

                    <Box display="flex" alignItems="center" gap={1} mb={1}>
                      <ScheduleIcon fontSize="small" color="action" />
                      <Typography variant="body2" color="text.secondary">
                        Ends: {quiz.endsAt ? format(quiz.endsAt.toDate(), 'MMM dd, yyyy HH:mm') : 'Not set'}
                      </Typography>
                    </Box>

                    {quiz.startsAt && (
                      <Box display="flex" alignItems="center" gap={1} mb={1}>
                        <Typography variant="body2" color="text.secondary">
                          Starts: {format(quiz.startsAt.toDate(), 'MMM dd, yyyy HH:mm')}
                        </Typography>
                      </Box>
                    )}
                  </CardContent>

                  <CardActions>
                    <Button
                      size="small"
                      onClick={() => {
                        console.log('Navigating to quiz details, quiz ID:', quiz.id)
                        navigate(`/quiz/${quiz.id}`)
                      }}
                    >
                      View Details
                    </Button>
                    <Button
                      size="small"
                      onClick={() => {
                        console.log('Navigating to quiz responses, quiz ID:', quiz.id)
                        navigate(`/quiz/${quiz.id}/responses`)
                      }}
                    >
                      Responses
                    </Button>
                  </CardActions>
                </Card>
              </Grid>
            )
          })}
        </Grid>
      )}

      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => {
          console.log('Menu: Navigating to quiz details, quiz ID:', selectedQuiz?.id)
          navigate(`/quiz/${selectedQuiz?.id}`)
          handleMenuClose()
        }}>
          Edit Quiz
        </MenuItem>
        <MenuItem onClick={() => {
          console.log('Menu: Navigating to quiz responses, quiz ID:', selectedQuiz?.id)
          navigate(`/quiz/${selectedQuiz?.id}/responses`)
          handleMenuClose()
        }}>
          View Responses
        </MenuItem>
        <MenuItem onClick={handleDeleteQuiz} sx={{ color: 'error.main' }}>
          Delete Quiz
        </MenuItem>
      </Menu>
    </Box>
  )
}

export default DashboardPage