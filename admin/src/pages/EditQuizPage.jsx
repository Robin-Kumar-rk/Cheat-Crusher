import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Box,
  Typography,
  TextField,
  Button,
  Card,
  CardContent,
  FormControl,
  FormLabel,
  RadioGroup,
  FormControlLabel,
  Radio,
  Switch,
  Grid,
  IconButton,
  Alert,
  Divider,
  CircularProgress
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  ArrowBack as ArrowBackIcon
} from '@mui/icons-material'
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns'
import { getQuiz, updateQuiz } from '../services/quizService'
import { auth } from '../services/firebase'

function EditQuizPage() {
  const { quizId } = useParams()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [initialLoading, setInitialLoading] = useState(true)
  const [error, setError] = useState('')

  const [quizData, setQuizData] = useState({
    title: '',
    startsAt: new Date(),
    endsAt: new Date(Date.now() + 2 * 60 * 60 * 1000), // 2 hours from now
    durationSec: 3600, // 1 hour
    allowLateUploadSec: 300, // 5 minutes
    maxLateSec: 1800, // 30 minutes max late submission
    allowJoinAfterStart: false,
    onAppSwitch: 'flag',
    showResultsAt: new Date(Date.now() + 3 * 60 * 60 * 1000), // 3 hours from now
    showAnswersWithMarks: false,
    shuffleQuestions: true,
    shuffleOptions: true,
    autoDeleteAfterDays: 7,
    editableUntilStart: true,
    preJoinFields: [],
    questions: []
  })

  // Roll number access controls removed: anyone can join any quiz

  useEffect(() => {
    loadQuizData()
  }, [quizId])

  const loadQuizData = async () => {
    try {
      setInitialLoading(true)
      console.log('Loading quiz data for editing, quizId:', quizId)
      
      const quiz = await getQuiz(quizId)
      console.log('Loaded quiz for editing:', quiz)
      
      if (quiz) {
        setQuizData({
          ...quiz,
          startsAt: quiz.startsAt?.toDate ? quiz.startsAt.toDate() : new Date(quiz.startsAt),
          endsAt: quiz.endsAt?.toDate ? quiz.endsAt.toDate() : new Date(quiz.endsAt),
          showResultsAt: quiz.showResultsAt?.toDate ? quiz.showResultsAt.toDate() : new Date(quiz.showResultsAt)
        })
        
        // Roll constraints removed
      } else {
        setError('Quiz not found')
      }
    } catch (error) {
      console.error('Error loading quiz for editing:', error)
      setError('Failed to load quiz data')
    } finally {
      setInitialLoading(false)
    }
  }

  const handleInputChange = (field, value) => {
    setQuizData(prev => ({
      ...prev,
      [field]: value
    }))
  }

  const addQuestion = () => {
    const newQuestion = {
      id: `q_${Date.now()}`,
      type: 'MCQ',
      text: '',
      options: [
        { id: `opt_${Date.now()}_1`, text: '' },
        { id: `opt_${Date.now()}_2`, text: '' }
      ],
      correct: [],
      weight: 1.0
    }

    setQuizData(prev => ({
      ...prev,
      questions: [...prev.questions, newQuestion]
    }))
  }

  const removeQuestion = (questionIndex) => {
    setQuizData(prev => ({
      ...prev,
      questions: prev.questions.filter((_, index) => index !== questionIndex)
    }))
  }

  const updateQuestion = (questionIndex, field, value) => {
    setQuizData(prev => ({
      ...prev,
      questions: prev.questions.map((q, index) => 
        index === questionIndex ? { ...q, [field]: value } : q
      )
    }))
  }

  const addOption = (questionIndex) => {
    const newOption = {
      id: `opt_${Date.now()}`,
      text: ''
    }

    setQuizData(prev => ({
      ...prev,
      questions: prev.questions.map((q, index) => 
        index === questionIndex 
          ? { ...q, options: [...q.options, newOption] }
          : q
      )
    }))
  }

  const removeOption = (questionIndex, optionIndex) => {
    setQuizData(prev => ({
      ...prev,
      questions: prev.questions.map((q, index) => 
        index === questionIndex 
          ? { ...q, options: q.options.filter((_, oIndex) => oIndex !== optionIndex) }
          : q
      )
    }))
  }

  const updateOption = (questionIndex, optionIndex, text) => {
    setQuizData(prev => ({
      ...prev,
      questions: prev.questions.map((q, qIndex) => 
        qIndex === questionIndex 
          ? {
              ...q,
              options: q.options.map((opt, oIndex) => 
                oIndex === optionIndex ? { ...opt, text } : opt
              )
            }
          : q
      )
    }))
  }

  const toggleCorrectOption = (questionIndex, optionId) => {
    const question = quizData.questions[questionIndex]
    let newCorrect = [...question.correct]

    if (question.type === 'MCQ') {
      // Single choice - replace the array
      newCorrect = [optionId]
    } else {
      // Multiple choice - toggle
      if (newCorrect.includes(optionId)) {
        newCorrect = newCorrect.filter(id => id !== optionId)
      } else {
        newCorrect.push(optionId)
      }
    }

    updateQuestion(questionIndex, 'correct', newCorrect)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      // Validate required fields
      if (!quizData.title.trim()) {
        throw new Error('Quiz title is required')
      }

      if (quizData.questions.length === 0) {
        throw new Error('At least one question is required')
      }

      const updatedQuizData = {
        ...quizData,
        updatedBy: auth.currentUser?.uid,
        updatedAt: new Date()
      }

      console.log('Updating quiz with data:', updatedQuizData)
      await updateQuiz(quizId, updatedQuizData)
      
      navigate(`/quiz/${quizId}`)
    } catch (error) {
      console.error('Error updating quiz:', error)
      setError(error.message)
    } finally {
      setLoading(false)
    }
  }

  if (initialLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="50vh">
        <CircularProgress />
      </Box>
    )
  }

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Box>
        <Box display="flex" alignItems="center" mb={3}>
          <IconButton onClick={() => navigate(`/quiz/${quizId}`)} sx={{ mr: 1 }}>
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h4" component="h1">
            Edit Quiz
          </Typography>
        </Box>

        {error && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {error}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            {/* Basic Information */}
            <Grid item xs={12}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Basic Information
                  </Typography>
                  
                  <TextField
                    fullWidth
                    label="Quiz Title"
                    value={quizData.title}
                    onChange={(e) => handleInputChange('title', e.target.value)}
                    margin="normal"
                    required
                  />

                  <Grid container spacing={2} sx={{ mt: 1 }}>
                    <Grid item xs={12} md={6}>
                      <DateTimePicker
                        label="Start Time"
                        value={quizData.startsAt}
                        onChange={(value) => handleInputChange('startsAt', value)}
                        slotProps={{
                          textField: {
                            fullWidth: true
                          }
                        }}
                      />
                    </Grid>
                    <Grid item xs={12} md={6}>
                      <DateTimePicker
                        label="End Time"
                        value={quizData.endsAt}
                        onChange={(value) => handleInputChange('endsAt', value)}
                        slotProps={{
                          textField: {
                            fullWidth: true
                          }
                        }}
                      />
                    </Grid>
                  </Grid>

                  <Grid container spacing={2} sx={{ mt: 1 }}>
                    <Grid item xs={12} md={6}>
                      <TextField
                        fullWidth
                        label="Late Upload Grace Period (seconds)"
                        type="number"
                        value={quizData.allowLateUploadSec}
                        onChange={(e) => handleInputChange('allowLateUploadSec', parseInt(e.target.value))}
                        margin="normal"
                      />
                    </Grid>
                  </Grid>

                  <Grid container spacing={2} sx={{ mt: 1 }}>
                    <Grid item xs={12} md={6}>
                      <TextField
                        fullWidth
                        label="Maximum Late Submission (seconds)"
                        type="number"
                        value={quizData.maxLateSec}
                        onChange={(e) => handleInputChange('maxLateSec', parseInt(e.target.value))}
                        margin="normal"
                        helperText="Maximum time allowed for late submissions after quiz ends"
                      />
                    </Grid>
                    <Grid item xs={12} md={6}>
                      <TextField
                        fullWidth
                        label="Auto Delete After (days)"
                        type="number"
                        value={quizData.autoDeleteAfterDays}
                        onChange={(e) => handleInputChange('autoDeleteAfterDays', parseInt(e.target.value))}
                        margin="normal"
                        helperText="Quiz will be automatically deleted after this many days"
                      />
                    </Grid>
                  </Grid>

                  {/* Editing is always allowed until start; remove toggle */}
                </CardContent>
              </Card>
            </Grid>

            {/* Settings */}
            <Grid item xs={12}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Quiz Settings
                  </Typography>

                  <FormControl component="fieldset" margin="normal">
                    <FormLabel component="legend">App Switch Policy</FormLabel>
                    <RadioGroup
                      value={quizData.onAppSwitch}
                      onChange={(e) => handleInputChange('onAppSwitch', e.target.value)}
                    >
                      <FormControlLabel value="flag" control={<Radio />} label="Flag for review" />
                      <FormControlLabel value="reset" control={<Radio />} label="Reset attempt" />
                      <FormControlLabel value="disqualify" control={<Radio />} label="Disqualify" />
                    </RadioGroup>
                  </FormControl>

                  <Box sx={{ mt: 2 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={quizData.allowJoinAfterStart}
                          onChange={(e) => handleInputChange('allowJoinAfterStart', e.target.checked)}
                        />
                      }
                      label="Allow joining after quiz starts"
                    />
                  </Box>

                  <Box sx={{ mt: 1 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={quizData.shuffleQuestions}
                          onChange={(e) => handleInputChange('shuffleQuestions', e.target.checked)}
                        />
                      }
                      label="Shuffle questions"
                    />
                  </Box>

                  <Box sx={{ mt: 1 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={quizData.shuffleOptions}
                          onChange={(e) => handleInputChange('shuffleOptions', e.target.checked)}
                        />
                      }
                      label="Shuffle options"
                    />
                  </Box>

                  {/* Roll number access control removed: anyone can join */}

                  {/* Result Declaration Settings */}
                  <Box sx={{ mt: 3 }}>
                    <Typography variant="subtitle1" gutterBottom>
                      Result Declaration Settings
                    </Typography>
                    <LocalizationProvider dateAdapter={AdapterDateFns}>
                      <DateTimePicker
                        label="Show Results At"
                        value={quizData.showResultsAt}
                        onChange={(newValue) => handleInputChange('showResultsAt', newValue)}
                        renderInput={(params) => <TextField {...params} fullWidth margin="normal" />}
                      />
                    </LocalizationProvider>
                    <Box sx={{ mt: 1 }}>
                      <FormControlLabel
                        control={
                          <Switch
                            checked={quizData.showAnswersWithMarks}
                            onChange={(e) => handleInputChange('showAnswersWithMarks', e.target.checked)}
                          />
                        }
                        label="Show answers with marks"
                      />
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            </Grid>

            {/* Pre-Join Form */}
            <Grid item xs={12}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Pre-Join Form
                  </Typography>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Choose which fields students must fill before starting the quiz.
                  </Typography>

                  {[
                    { id: 'name', label: 'Name' },
                    { id: 'email', label: 'Email' },
                    { id: 'roll', label: 'Roll Number' },
                    { id: 'section', label: 'Section' }
                  ].map((field) => {
                    const exists = quizData.preJoinFields?.find(f => f.id === field.id)
                    const included = Boolean(exists)
                    const required = exists?.required || false
                    const type = exists?.type || 'text'
                    return (
                      <Box key={field.id} display="flex" alignItems="center" gap={2} mb={1}>
                        <FormControlLabel
                          control={
                            <Switch
                              checked={included}
                              onChange={(e) => {
                                const on = e.target.checked
                                let next = [...(quizData.preJoinFields || [])]
                                if (on && !exists) {
                                  next.push({ id: field.id, label: field.label, type, required })
                                } else if (!on && exists) {
                                  next = next.filter(f => f.id !== field.id)
                                }
                                handleInputChange('preJoinFields', next)
                              }}
                            />
                          }
                          label={`Include ${field.label}`}
                        />
                        <FormControlLabel
                          control={
                            <Switch
                              checked={required}
                              onChange={(e) => {
                                const next = (quizData.preJoinFields || []).map(f =>
                                  f.id === field.id ? { ...f, required: e.target.checked } : f
                                )
                                handleInputChange('preJoinFields', next)
                              }}
                              disabled={!included}
                            />
                          }
                          label="Required"
                        />
                      </Box>
                    )
                  })}
                </CardContent>
              </Card>
            </Grid>

            {/* Questions */}
            <Grid item xs={12}>
              <Card>
                <CardContent>
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                    <Typography variant="h6">
                      Questions ({quizData.questions.length})
                    </Typography>
                    <Button
                      variant="outlined"
                      startIcon={<AddIcon />}
                      onClick={addQuestion}
                    >
                      Add Question
                    </Button>
                  </Box>

                  {quizData.questions.map((question, questionIndex) => (
                    <Card key={question.id} variant="outlined" sx={{ mb: 2 }}>
                      <CardContent>
                        <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
                          <Typography variant="subtitle1">
                            Question {questionIndex + 1}
                          </Typography>
                          <IconButton
                            size="small"
                            onClick={() => removeQuestion(questionIndex)}
                            color="error"
                          >
                            <DeleteIcon />
                          </IconButton>
                        </Box>

                        <TextField
                          fullWidth
                          label="Question Text"
                          value={question.text}
                          onChange={(e) => updateQuestion(questionIndex, 'text', e.target.value)}
                          margin="normal"
                          multiline
                          rows={2}
                        />

                        <FormControl component="fieldset" margin="normal">
                          <FormLabel component="legend">Question Type</FormLabel>
                          <RadioGroup
                            value={question.type}
                            onChange={(e) => updateQuestion(questionIndex, 'type', e.target.value)}
                            row
                          >
                            <FormControlLabel value="MCQ" control={<Radio />} label="Single Choice" />
                            <FormControlLabel value="MSQ" control={<Radio />} label="Multiple Choice" />
                            <FormControlLabel value="TEXT" control={<Radio />} label="Text Answer" />
                          </RadioGroup>
                        </FormControl>

                        {question.type !== 'TEXT' && (
                          <Box sx={{ mt: 2 }}>
                            <Typography variant="subtitle2" gutterBottom>
                              Options (click to mark as correct)
                            </Typography>
                            {question.options.map((option, optionIndex) => (
                              <Box key={option.id} display="flex" alignItems="center" gap={1} mb={1}>
                                <Button
                                  variant={question.correct.includes(option.id) ? "contained" : "outlined"}
                                  size="small"
                                  onClick={() => toggleCorrectOption(questionIndex, option.id)}
                                  sx={{ minWidth: 60 }}
                                >
                                  {String.fromCharCode(65 + optionIndex)}
                                </Button>
                                <TextField
                                  fullWidth
                                  size="small"
                                  value={option.text}
                                  onChange={(e) => updateOption(questionIndex, optionIndex, e.target.value)}
                                  placeholder={`Option ${String.fromCharCode(65 + optionIndex)}`}
                                />
                                {question.options.length > 2 && (
                                  <IconButton
                                    size="small"
                                    onClick={() => removeOption(questionIndex, optionIndex)}
                                    color="error"
                                  >
                                    <DeleteIcon />
                                  </IconButton>
                                )}
                              </Box>
                            ))}
                            <Button
                              size="small"
                              onClick={() => addOption(questionIndex)}
                              startIcon={<AddIcon />}
                            >
                              Add Option
                            </Button>
                          </Box>
                        )}

                        <TextField
                          label="Weight"
                          type="number"
                          value={question.weight}
                          onChange={(e) => updateQuestion(questionIndex, 'weight', parseFloat(e.target.value))}
                          sx={{ mt: 2, width: 120 }}
                          inputProps={{ min: 0.1, step: 0.1 }}
                        />
                      </CardContent>
                    </Card>
                  ))}

                  {quizData.questions.length === 0 && (
                    <Box textAlign="center" py={4}>
                      <Typography color="text.secondary">
                        No questions added yet. Click "Add Question" to get started.
                      </Typography>
                    </Box>
                  )}
                </CardContent>
              </Card>
            </Grid>

            {/* Submit */}
            <Grid item xs={12}>
              <Box display="flex" gap={2}>
                <Button
                  variant="outlined"
                  onClick={() => navigate(`/quiz/${quizId}`)}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  disabled={loading}
                >
                  {loading ? 'Updating...' : 'Update Quiz'}
                </Button>
              </Box>
            </Grid>
          </Grid>
        </form>
      </Box>
    </LocalizationProvider>
  )
}

export default EditQuizPage