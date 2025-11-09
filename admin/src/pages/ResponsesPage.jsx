import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Box,
  Typography,
  Card,
  CardContent,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  IconButton,
  Alert,
  CircularProgress,
  TextField,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tooltip,
  FormControlLabel,
  Switch
} from '@mui/material'
import {
  ArrowBack as ArrowBackIcon,
  Download as DownloadIcon,
  Edit as EditIcon,
  Warning as WarningIcon,
  Flag as FlagIcon,
  Block as BlockIcon
} from '@mui/icons-material'
import { getQuiz, getQuizResponses, updateResponseGrade } from '../services/quizService'
import { exportResponsesAsCSV } from '../utils/csvExport'
import { format } from 'date-fns'

function ResponsesPage() {
  const { quizId } = useParams()
  const navigate = useNavigate()
  const [quiz, setQuiz] = useState(null)
  const [responses, setResponses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [gradeDialogOpen, setGradeDialogOpen] = useState(false)
  const [selectedResponse, setSelectedResponse] = useState(null)
  const [newScore, setNewScore] = useState('')
  const [exportDialogOpen, setExportDialogOpen] = useState(false)
  const [selectedColumns, setSelectedColumns] = useState([
    'Roll Number', 'Name', 'Section', 'Submitted At', 'Score', 'Grade Status'
  ])

  useEffect(() => {
    loadData()
  }, [quizId])

  const loadData = async () => {
    try {
      setLoading(true)
      setError('')
      
      // Load quiz data first
      console.log('ResponsesPage: Loading quiz data for ID:', quizId)
      const quizData = await getQuiz(quizId)
      console.log('ResponsesPage: Quiz data loaded successfully:', quizData)
      setQuiz(quizData)
      
      // Load responses data separately to avoid failing if responses don't exist
      try {
        console.log('ResponsesPage: Loading responses data for quiz ID:', quizId)
        const responsesData = await getQuizResponses(quizId)
        console.log('ResponsesPage: Responses data loaded successfully:', responsesData)
        setResponses(responsesData)
      } catch (responsesError) {
        console.warn('ResponsesPage: Failed to load responses:', responsesError.message)
        setResponses([]) // Set empty array if responses fail to load
      }
      
    } catch (error) {
      console.error('ResponsesPage: Failed to load quiz data:', error)
      setError('Failed to load quiz: ' + error.message)
      setQuiz(null)
    } finally {
      setLoading(false)
    }
  }

  const handleExportCSV = async () => {
    try {
      await exportResponsesAsCSV(quiz, responses, selectedColumns)
      setExportDialogOpen(false)
    } catch (error) {
      setError('Failed to export CSV: ' + error.message)
    }
  }

  const handleGradeResponse = (response) => {
    setSelectedResponse(response)
    setNewScore(response.score?.toString() || '')
    setGradeDialogOpen(true)
  }

  const handleSaveGrade = async () => {
    try {
      const score = parseFloat(newScore)
      if (isNaN(score) || score < 0 || score > 100) {
        setError('Please enter a valid score between 0 and 100')
        return
      }

      await updateResponseGrade(selectedResponse.id, score)
      
      // Update local state
      setResponses(responses.map(r => 
        r.id === selectedResponse.id 
          ? { ...r, score, gradeStatus: 'graded' }
          : r
      ))

      setGradeDialogOpen(false)
      setSelectedResponse(null)
      setNewScore('')
    } catch (error) {
      setError('Failed to update grade: ' + error.message)
    }
  }

  const getStatusChip = (response) => {
    if (response.disqualified) {
      return <Chip label="Disqualified" color="error" size="small" />
    }
    if (response.flagged) {
      return <Chip label="Flagged" color="warning" size="small" />
    }
    if (response.gradeStatus === 'pending') {
      return <Chip label="Pending" color="info" size="small" />
    }
    if (response.gradeStatus === 'graded') {
      return <Chip label="Graded" color="success" size="small" />
    }
    return <Chip label="Auto-graded" color="default" size="small" />
  }

  if (loading) {
    console.log('ResponsesPage: Rendering loading state')
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    )
  }

  if (error) {
    console.log('ResponsesPage: Rendering error state:', error)
    return (
      <Box>
        <Alert severity="error">{error}</Alert>
      </Box>
    )
  }

  if (!quiz) {
    console.log('ResponsesPage: Rendering quiz not found state, quiz:', quiz)
    return (
      <Box>
        <Alert severity="error">Quiz not found</Alert>
      </Box>
    )
  }

  console.log('ResponsesPage: Rendering quiz responses page, quiz:', quiz, 'responses:', responses)

  return (
    <Box>
      <Box display="flex" alignItems="center" mb={3}>
        <IconButton onClick={() => navigate(`/quiz/${quizId}`)} sx={{ mr: 1 }}>
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>
          Responses - {quiz.title}
        </Typography>
        <Button
          variant="contained"
          startIcon={<DownloadIcon />}
          onClick={() => setExportDialogOpen(true)}
          disabled={responses.length === 0}
        >
          Export CSV
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Summary Cards */}
      <Box display="flex" gap={2} mb={3}>
        <Card sx={{ minWidth: 120 }}>
          <CardContent sx={{ textAlign: 'center', py: 2 }}>
            <Typography variant="h4" color="primary">
              {responses.length}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Total
            </Typography>
          </CardContent>
        </Card>
        
        <Card sx={{ minWidth: 120 }}>
          <CardContent sx={{ textAlign: 'center', py: 2 }}>
            <Typography variant="h4" color="success.main">
              {responses.filter(r => r.score !== null && !r.disqualified).length}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Graded
            </Typography>
          </CardContent>
        </Card>

        <Card sx={{ minWidth: 120 }}>
          <CardContent sx={{ textAlign: 'center', py: 2 }}>
            <Typography variant="h4" color="warning.main">
              {responses.filter(r => r.flagged).length}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Flagged
            </Typography>
          </CardContent>
        </Card>

        <Card sx={{ minWidth: 120 }}>
          <CardContent sx={{ textAlign: 'center', py: 2 }}>
            <Typography variant="h4" color="error.main">
              {responses.filter(r => r.disqualified).length}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Disqualified
            </Typography>
          </CardContent>
        </Card>
      </Box>

      {/* Responses Table */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            All Responses
          </Typography>

          {responses.length === 0 ? (
            <Box textAlign="center" py={6}>
              <Typography color="text.secondary">
                No responses yet
              </Typography>
            </Box>
          ) : (
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Roll Number</TableCell>
                    <TableCell>Submitted At</TableCell>
                    <TableCell>Score</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Issues</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {responses.map((response) => (
                    <TableRow key={response.id}>
                      <TableCell>
                        <Typography variant="body2" fontWeight="medium">
                          {response.rollNumber || 'N/A'}
                        </Typography>
                        {response.userId ? (
                          <Typography variant="caption" color="text.secondary">
                            {response.userId.substring(0, 8)}...
                          </Typography>
                        ) : null}
                      </TableCell>
                      
                      <TableCell>
                        {response.clientSubmittedAt ? (
                          <Box>
                            <Typography variant="body2">
                              {format(response.clientSubmittedAt.toDate(), 'MMM dd, HH:mm')}
                            </Typography>
                            {response.serverUploadedAt && (
                              <Typography variant="caption" color="text.secondary">
                                Uploaded: {format(response.serverUploadedAt.toDate(), 'HH:mm:ss')}
                              </Typography>
                            )}
                          </Box>
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            Not submitted
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell>
                        {response.score != null ? (
                          <Typography variant="body2" fontWeight="medium">
                            {Number(response.score).toFixed(1)}%
                          </Typography>
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            Not graded
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell>
                        {getStatusChip(response)}
                      </TableCell>

                      <TableCell>
                        <Box display="flex" gap={0.5}>
                          {response.flagged && (
                            <Tooltip title="Flagged for review">
                              <FlagIcon color="warning" fontSize="small" />
                            </Tooltip>
                          )}
                          {response.disqualified && (
                            <Tooltip title="Disqualified">
                              <BlockIcon color="error" fontSize="small" />
                            </Tooltip>
                          )}
                          {response.appSwitchEvents && response.appSwitchEvents.length > 0 && (
                            <Tooltip title={`${response.appSwitchEvents.length} app switch events`}>
                              <WarningIcon color="warning" fontSize="small" />
                            </Tooltip>
                          )}
                        </Box>
                      </TableCell>

                      <TableCell>
                        <IconButton
                          size="small"
                          onClick={() => handleGradeResponse(response)}
                          disabled={response.disqualified}
                        >
                          <EditIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      {/* Export Options Dialog */}
      <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)}>
        <DialogTitle>Choose export columns</DialogTitle>
        <DialogContent>
          <Box>
            {[
              'Roll Number', 'Name', 'Email', 'Section',
              'Submitted At', 'Uploaded At', 'Score', 'Grade Status',
              'Flagged', 'Disqualified', 'App Switch Count', 'Questions'
            ].map((col) => (
              <FormControlLabel
                key={col}
                control={
                  <Switch
                    checked={selectedColumns.includes(col)}
                    onChange={(e) => {
                      const on = e.target.checked
                      setSelectedColumns(prev => {
                        if (on) return [...prev, col]
                        return prev.filter(c => c !== col)
                      })
                    }}
                  />
                }
                label={col}
              />
            ))}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setExportDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleExportCSV}>Download</Button>
        </DialogActions>
      </Dialog>

      {/* Grade Dialog */}
      <Dialog open={gradeDialogOpen} onClose={() => setGradeDialogOpen(false)}>
        <DialogTitle>
          Grade Response
        </DialogTitle>
        <DialogContent>
          {selectedResponse && (
            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Student: {selectedResponse.rollNumber || 'N/A'}
              </Typography>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Current Score: {selectedResponse.score?.toFixed(1) || 'Not graded'}%
              </Typography>
              
              <TextField
                fullWidth
                label="New Score (%)"
                type="number"
                value={newScore}
                onChange={(e) => setNewScore(e.target.value)}
                margin="normal"
                inputProps={{ min: 0, max: 100, step: 0.1 }}
                helperText="Enter a score between 0 and 100"
              />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setGradeDialogOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleSaveGrade} variant="contained">
            Save Grade
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

export default ResponsesPage