import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Box,
  Typography,
  Card,
  CardContent,
  Button,
  Chip,
  Grid,
  Alert,
  CircularProgress,
  IconButton,
  Divider,
  TextField
} from '@mui/material'
import {
  ArrowBack as ArrowBackIcon,
  Edit as EditIcon,
  People as PeopleIcon,
  Download as DownloadIcon,
  Share as ShareIcon
} from '@mui/icons-material'
import { getQuiz, getQuizResponses, cloneQuiz } from '../services/quizService'
import { generateJoinCode } from '../utils/joinCode'
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns'
import { exportResponsesAsCSV } from '../utils/csvExport'
import { format } from 'date-fns'

function QuizDetailsPage() {
  const { quizId } = useParams()
  const navigate = useNavigate()
  const [quiz, setQuiz] = useState(null)
  const [responses, setResponses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [joinStartTime, setJoinStartTime] = useState(new Date())
  const [generatedJoinCode, setGeneratedJoinCode] = useState('')
  const [genError, setGenError] = useState('')

  useEffect(() => {
    loadQuizData()
  }, [quizId])

  const loadQuizData = async () => {
    try {
      setLoading(true)
      setError('') // Clear previous errors
      
      console.log('Loading quiz data for ID:', quizId)
      
      // Load quiz data first
      const quizData = await getQuiz(quizId)
      console.log('Quiz data loaded successfully:', quizData)
      setQuiz(quizData)
      
      // Then load responses
      try {
        const responsesData = await getQuizResponses(quizId)
        console.log('Responses loaded successfully:', responsesData.length, 'responses')
        setResponses(responsesData)
      } catch (responseError) {
        console.warn('Failed to load responses, but quiz loaded successfully:', responseError)
        setResponses([]) // Set empty responses if they fail to load
        // Don't set error here since quiz loaded successfully
      }
      
    } catch (error) {
      console.error('Failed to load quiz data:', error)
      setError('Failed to load quiz data: ' + error.message)
      setQuiz(null) // Explicitly set quiz to null on error
    } finally {
      setLoading(false)
    }
  }

  const handleExportCSV = async () => {
    try {
      await exportResponsesAsCSV(quiz, responses)
    } catch (error) {
      setError('Failed to export CSV: ' + error.message)
    }
  }

  const getQuizStatus = () => {
    if (!quiz.startsAt || !quiz.endsAt) return { status: 'draft', color: 'default' }

    const now = new Date()
    const startTime = quiz.startsAt.toDate()
    const endTime = quiz.endsAt.toDate()

    if (now < startTime) {
      return { status: 'scheduled', color: 'info' }
    } else if (now >= startTime && now <= endTime) {
      return { status: 'active', color: 'success' }
    } else {
      return { status: 'completed', color: 'default' }
    }
  }

  const copyQuizCode = () => {
    navigator.clipboard.writeText(quiz.downloadCode || quiz.code)
    // You could add a snackbar notification here
  }

  if (loading) {
    console.log('QuizDetailsPage: Loading state, quiz:', quiz)
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    )
  }

  if (error) {
    console.log('QuizDetailsPage: Error state:', error)
    return (
      <Box>
        <Alert severity="error">{error}</Alert>
      </Box>
    )
  }

  if (!quiz) {
    console.log('QuizDetailsPage: No quiz found, quiz state:', quiz)
    return (
      <Box>
        <Alert severity="error">Quiz not found</Alert>
      </Box>
    )
  }

  console.log('QuizDetailsPage: Rendering quiz:', quiz)

  const status = getQuizStatus()

  return (
    <Box>
      <Box display="flex" alignItems="center" mb={3}>
        <IconButton onClick={() => navigate('/dashboard')} sx={{ mr: 1 }}>
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>
          {quiz.title}
        </Typography>
        {/* Edit disabled per spec; only cloning allowed */}
        <Button
          variant="contained"
          startIcon={<PeopleIcon />}
          onClick={() => navigate(`/quiz/${quizId}/responses`)}
        >
          View Responses ({responses.length})
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      <Grid container spacing={3}>
        {/* Quiz Overview */}
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Quiz Overview
              </Typography>

              <Box display="flex" gap={1} mb={3}>
                <Chip
                  label={status.status.toUpperCase()}
                  color={status.color}
                />
                <Chip
                  label={quiz.downloadCode || quiz.code}
                  variant="outlined"
                  onClick={copyQuizCode}
                  icon={<ShareIcon />}
                />
              </Box>

              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">
                    Timer
                  </Typography>
                  <Typography variant="body1">
                    {quiz.timerMinutes || Math.round((quiz.durationSec || 0)/60)} minutes
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">
                    Latency
                  </Typography>
                  <Typography variant="body1">
                    {quiz.latencyMinutes || Math.round((quiz.allowLateUploadSec || 0)/60)} minutes
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">
                    Questions
                  </Typography>
                  <Typography variant="body1">
                    {quiz.questions?.length || 0}
                  </Typography>
                </Grid>
              </Grid>

              <Divider sx={{ my: 3 }} />

              <Typography variant="h6" gutterBottom>
                Settings
              </Typography>

              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">
                    App Switch Policy
                  </Typography>
                  <Typography variant="body1" sx={{ textTransform: 'capitalize' }}>
                    {quiz.onAppSwitch}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">
                    Late Upload Grace
                  </Typography>
                  <Typography variant="body1">
                    {quiz.allowLateUploadSec} seconds
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">
                    Shuffle Questions
                  </Typography>
                  <Typography variant="body1">
                    {quiz.shuffleQuestions ? 'Yes' : 'No'}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">
                    Shuffle Options
                  </Typography>
                  <Typography variant="body1">
                    {quiz.shuffleOptions ? 'Yes' : 'No'}
                  </Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Statistics */}
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Statistics
              </Typography>

              <Box mb={2}>
                <Typography variant="h3" color="primary">
                  {responses.length}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Total Responses
                </Typography>
              </Box>

              <Box mb={2}>
                <Typography variant="h4" color="success.main">
                  {responses.filter(r => r.score !== null).length}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Graded
                </Typography>
              </Box>

              <Box mb={2}>
                <Typography variant="h4" color="warning.main">
                  {responses.filter(r => r.flagged).length}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Flagged
                </Typography>
              </Box>

              <Box mb={3}>
                <Typography variant="h4" color="error.main">
                  {responses.filter(r => r.disqualified).length}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Disqualified
                </Typography>
              </Box>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={handleExportCSV}
                disabled={responses.length === 0}
              >
                Export CSV
              </Button>
            </CardContent>
          </Card>

          {/* Get a Join Code */}
          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Get a Join Code
              </Typography>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Select a start time. The join code mixes the unlock password with the time in an unreadable format.
              </Typography>
              <LocalizationProvider dateAdapter={AdapterDateFns}>
                <Grid container spacing={2} sx={{ mt: 1 }}>
                  <Grid item xs={12}>
                    <DateTimePicker
                      label="Select Start Time"
                      value={joinStartTime}
                      onChange={(value) => setJoinStartTime(value)}
                    />
                  </Grid>
                  <Grid item xs={12}>
                    <Button
                      variant="contained"
                      onClick={async () => {
                        try {
                          setGenError('')
                          const pwd = quiz.unlockPassword || (quiz.rawJson ? JSON.parse(quiz.rawJson).unlockPassword : '')
                          const code = await generateJoinCode(pwd, joinStartTime)
                          setGeneratedJoinCode(code)
                        } catch (e) {
                          setGenError(e.message || 'Failed to generate join code')
                        }
                      }}
                    >
                      Get a Join Code
                    </Button>
                  </Grid>
                </Grid>
              </LocalizationProvider>
              {genError && (
                <Alert severity="error" sx={{ mt: 2 }}>{genError}</Alert>
              )}
              {generatedJoinCode && (
                <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mt: 2 }}>
                  <TextField
                    fullWidth
                    label="Generated Join Code"
                    value={generatedJoinCode}
                    InputProps={{ readOnly: true }}
                  />
                  <Button variant="outlined" onClick={() => navigator.clipboard.writeText(generatedJoinCode)}>Copy</Button>
                </Box>
              )}
            </CardContent>
          </Card>

          {/* Data Retention Warning */}
          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom color="warning.main">
                Data Retention
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Responses will be automatically deleted {quiz.autoDeleteAfterDays || 7} days after the quiz ends.
              </Typography>
              {quiz.endsAt && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  Deletion date: {format(
                    new Date(quiz.endsAt.toDate().getTime() + (quiz.autoDeleteAfterDays || 7) * 24 * 60 * 60 * 1000),
                    'MMM dd, yyyy'
                  )}
                </Typography>
              )}
            </CardContent>
          </Card>

          {/* Actions */}
          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Actions
              </Typography>
              <Grid container spacing={2}>
                <Grid item>
                  <Button
                    variant="outlined"
                    startIcon={<ShareIcon />}
                    onClick={async () => {
                      try {
                        const cloned = await cloneQuiz(quiz.id, {
                          startsAt: quiz.startsAt,
                          endsAt: quiz.endsAt,
                          onAppSwitch: quiz.onAppSwitch,
                          shuffleQuestions: quiz.shuffleQuestions,
                          shuffleOptions: quiz.shuffleOptions,
                          preJoinFields: quiz.preJoinFields || []
                        })
                        navigate(`/quiz/${cloned.id}/edit`)
                      } catch (e) {
                        console.error('Clone failed', e)
                        alert('Failed to clone quiz: ' + (e.message || e))
                      }
                    }}
                  >
                    Clone Quiz
                  </Button>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Questions Preview */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Questions ({quiz.questions?.length || 0})
              </Typography>

              {quiz.questions?.map((question, index) => (
                <Card key={question.id} variant="outlined" sx={{ mb: 2 }}>
                  <CardContent>
                    <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={1}>
                      <Typography variant="subtitle1">
                        Question {index + 1}
                      </Typography>
                      <Chip
                        label={question.type}
                        size="small"
                        variant="outlined"
                      />
                    </Box>

                    <Typography variant="body1" paragraph>
                      {question.text}
                    </Typography>

                    {question.type !== 'TEXT' && question.options && (
                      <Box>
                        {question.options.map((option, optionIndex) => (
                          <Box
                            key={option.id}
                            display="flex"
                            alignItems="center"
                            gap={1}
                            mb={0.5}
                          >
                            <Typography
                              variant="body2"
                              sx={{
                                minWidth: 20,
                                fontWeight: question.correct?.includes(option.id) ? 'bold' : 'normal',
                                color: question.correct?.includes(option.id) ? 'success.main' : 'text.secondary'
                              }}
                            >
                              {String.fromCharCode(65 + optionIndex)}.
                            </Typography>
                            <Typography
                              variant="body2"
                              sx={{
                                fontWeight: question.correct?.includes(option.id) ? 'bold' : 'normal',
                                color: question.correct?.includes(option.id) ? 'success.main' : 'text.primary'
                              }}
                            >
                              {option.text}
                            </Typography>
                          </Box>
                        ))}
                      </Box>
                    )}

                    <Typography variant="caption" color="text.secondary">
                      Weight: {question.weight}
                    </Typography>
                  </CardContent>
                </Card>
              ))}

              {(!quiz.questions || quiz.questions.length === 0) && (
                <Typography color="text.secondary" textAlign="center" py={4}>
                  No questions added yet.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

export default QuizDetailsPage
