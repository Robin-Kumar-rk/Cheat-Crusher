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
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Tooltip
} from '@mui/material'
import {
  Add as AddIcon,
  MoreVert as MoreVertIcon,
  People as PeopleIcon,
  Quiz as QuizIcon,
  Schedule as ScheduleIcon,
  Warning as WarningIcon,
  Visibility as VisibilityIcon,
  ContentCopy as ContentCopyIcon,
  Key as KeyIcon
} from '@mui/icons-material'
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns'
import { generateJoinCode } from '../utils/joinCode'
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
  const [joinDialogOpen, setJoinDialogOpen] = useState(false)
  const [joinDialogQuiz, setJoinDialogQuiz] = useState(null)
  const [joinStartTime, setJoinStartTime] = useState(new Date())
  const [generatedJoinCode, setGeneratedJoinCode] = useState('')
  const [genError, setGenError] = useState('')

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

                    <Box display="flex" gap={1} mb={2} sx={{ alignItems: 'center' }}>
                      <Chip
                        label={status.status.toUpperCase()}
                        color={status.color}
                        size="small"
                      />
                      <Tooltip title="Copy Download Code">
                        <Chip
                          label={quiz.downloadCode || quiz.code}
                          variant="outlined"
                          size="small"
                          onClick={() => navigator.clipboard.writeText(quiz.downloadCode || quiz.code)}
                        />
                      </Tooltip>
                      {/* Answer Code Chip */}
                      {(() => {
                        try {
                          const ans = quiz.answerViewPassword || (quiz.rawJson ? JSON.parse(quiz.rawJson).answerViewPassword : '')
                          return ans ? (
                            <Tooltip title="Copy Answer Code">
                              <Chip
                                icon={<KeyIcon />}
                                label={`ANS: ${ans}`}
                                variant="outlined"
                                size="small"
                                onClick={() => navigator.clipboard.writeText(ans)}
                              />
                            </Tooltip>
                          ) : null
                        } catch {
                          return null
                        }
                      })()}
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
                      <Typography variant="body2" color="text.secondary">
                        Timer: {quiz.timerMinutes || Math.round((quiz.durationSec || 0)/60)} min • Latency: {quiz.latencyMinutes || Math.round((quiz.allowLateUploadSec || 0)/60)} min
                      </Typography>
                    </Box>
                    <Box display="flex" alignItems="center" gap={1} mb={1}>
                      <Typography variant="body2" color="text.secondary">
                        Answer Code: {quiz.answerViewPassword || (() => { try { const raw = quiz.rawJson ? JSON.parse(quiz.rawJson) : {}; return raw.answerViewPassword || '—' } catch { return '—' } })()}
                      </Typography>
                      <Button size="small" onClick={() => {
                        try {
                          const code = quiz.answerViewPassword || (quiz.rawJson ? JSON.parse(quiz.rawJson).answerViewPassword : '')
                          if (code) navigator.clipboard.writeText(code)
                        } catch {}
                      }}>Copy</Button>
                    </Box>
                  </CardContent>

                  <CardActions sx={{ justifyContent: 'space-between', px: 2 }}>
                    <Button
                      size="small"
                      variant="text"
                      startIcon={<VisibilityIcon />}
                      onClick={() => navigate(`/quiz/${quiz.id}`)}
                    >
                      View
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
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => {
                        setJoinDialogQuiz(quiz)
                        setJoinDialogOpen(true)
                        setGeneratedJoinCode('')
                        setGenError('')
                      }}
                    >
                      Get a Join Code
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
        {/* Remove Edit Quiz option per spec */}
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

      {/* Join Code Dialog */}
      <Dialog open={joinDialogOpen} onClose={() => setJoinDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Get a Join Code</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Select a start time. The join code mixes the unlock password with the time in an unreadable format.
          </Typography>
          <LocalizationProvider dateAdapter={AdapterDateFns}>
            <DateTimePicker
              label="Select Start Time"
              value={joinStartTime}
              onChange={(value) => setJoinStartTime(value)}
            />
          </LocalizationProvider>
          {genError && (
            <Alert severity="error" sx={{ mt: 2 }}>{genError}</Alert>
          )}
          {generatedJoinCode && (
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 2 }}>
              <TextField
                fullWidth
                label="Join Code"
                value={generatedJoinCode}
                InputProps={{ readOnly: true }}
              />
              <Tooltip title="Copy">
                <IconButton onClick={() => navigator.clipboard.writeText(generatedJoinCode)}>
                  <ContentCopyIcon />
                </IconButton>
              </Tooltip>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setJoinDialogOpen(false)}>Close</Button>
          <Button
            variant="contained"
            onClick={async () => {
              try {
                setGenError('')
                const quiz = joinDialogQuiz
                const raw = quiz?.rawJson ? JSON.parse(quiz.rawJson) : {}
                const pwd = quiz?.unlockPassword || raw.unlockPassword || ''
                const code = await generateJoinCode(pwd, joinStartTime)
                setGeneratedJoinCode(code)
              } catch (e) {
                setGenError(e.message || 'Failed to generate join code')
              }
            }}
          >
            Generate
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

export default DashboardPage
