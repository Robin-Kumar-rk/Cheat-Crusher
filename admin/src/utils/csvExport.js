import { saveAs } from 'file-saver'

export const exportResponsesAsCSV = (quiz, responses, selectedColumns = []) => {
  try {
    // Default fallback columns (teacher-friendly)
    const defaultColumns = [
      'Roll Number',
      'Name',
      'Section',
      'Submitted At',
      'Score',
      'Grade Status'
    ]
    const baseColumns = selectedColumns?.length ? selectedColumns : defaultColumns
    const includeQuestions = baseColumns.includes('Questions')
    const headers = baseColumns.filter(col => col !== 'Questions')
    if (includeQuestions) {
      quiz.questions.forEach((question, index) => {
        headers.push(`Q${index + 1}: ${question.text.substring(0, 50)}...`)
      })
    }

    // Prepare CSV rows
    const rows = responses.map(response => {
      const row = []
      baseColumns.forEach(col => {
        switch (col) {
          // Removed: Response ID, User ID
          case 'Roll Number':
            row.push(response.rollNumber || 'N/A')
            break
          case 'Name':
            row.push(response.studentInfo?.name || 'N/A')
            break
          case 'Email':
            row.push(response.studentInfo?.email || 'N/A')
            break
          case 'Section':
            row.push(response.studentInfo?.section || 'N/A')
            break
          case 'Submitted At':
            row.push(response.clientSubmittedAt ? new Date(response.clientSubmittedAt.seconds * 1000).toLocaleString() : 'N/A')
            break
          case 'Uploaded At':
            row.push(response.serverUploadedAt ? new Date(response.serverUploadedAt.seconds * 1000).toLocaleString() : 'N/A')
            break
          case 'Score':
            row.push(response.score !== null ? Number(response.score).toFixed(2) : 'N/A')
            break
          case 'Grade Status':
            row.push(response.gradeStatus || 'N/A')
            break
          case 'Flagged':
            row.push(response.flagged ? 'Yes' : 'No')
            break
          case 'Disqualified':
            row.push(response.disqualified ? 'Yes' : 'No')
            break
          case 'App Switch Count':
            row.push(response.appSwitchEvents ? response.appSwitchEvents.length : 0)
            break
          // Removed: Device ID, Policy
          case 'Questions':
            // handled below
            break
          default:
            row.push('')
        }
      })

      // Add answers for each question
      if (includeQuestions) {
        quiz.questions.forEach(question => {
          const answer = response.answers?.find(a => a.questionId === question.id)
          if (answer) {
            if (answer.answerText) {
              // Text answer
              row.push(answer.answerText)
            } else if (answer.optionIds && answer.optionIds.length > 0) {
              // MCQ/MSQ answer - convert option IDs to text
              const selectedOptions = answer.optionIds.map(optionId => {
                const option = question.options.find(opt => opt.id === optionId)
                return option ? option.text : optionId
              })
              row.push(selectedOptions.join('; '))
            } else {
              row.push('No answer')
            }
          } else {
            row.push('No answer')
          }
        })
      }

      return row
    })

    // Convert to CSV format
    const csvContent = [headers, ...rows]
      .map(row => row.map(field => `"${String(field).replace(/"/g, '""')}"`).join(','))
      .join('\n')

    // Create and download file
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const fileName = `${quiz.title.replace(/[^a-z0-9]/gi, '_').toLowerCase()}_responses_${new Date().toISOString().split('T')[0]}.csv`
    
    saveAs(blob, fileName)
    
    return true
  } catch (error) {
    console.error('Error exporting CSV:', error)
    throw new Error('Failed to export CSV file')
  }
}

export const exportQuizSummaryAsCSV = (quizzes) => {
  try {
    const headers = [
      'Quiz ID',
      'Title',
      'Code',
      'Created At',
      'Starts At',
      'Ends At',
      'Duration (minutes)',
      'Questions Count',
      'Total Responses',
      'Average Score'
    ]

    const rows = quizzes.map(quiz => [
      quiz.id,
      quiz.title,
      quiz.code,
      quiz.createdAt ? new Date(quiz.createdAt.seconds * 1000).toLocaleString() : 'N/A',
      quiz.startsAt ? new Date(quiz.startsAt.seconds * 1000).toLocaleString() : 'N/A',
      quiz.endsAt ? new Date(quiz.endsAt.seconds * 1000).toLocaleString() : 'N/A',
      Math.round(quiz.durationSec / 60),
      quiz.questions ? quiz.questions.length : 0,
      quiz.responseCount || 0,
      quiz.averageScore ? quiz.averageScore.toFixed(2) : 'N/A'
    ])

    const csvContent = [headers, ...rows]
      .map(row => row.map(field => `"${String(field).replace(/"/g, '""')}"`).join(','))
      .join('\n')

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const fileName = `quiz_summary_${new Date().toISOString().split('T')[0]}.csv`
    
    saveAs(blob, fileName)
    
    return true
  } catch (error) {
    console.error('Error exporting quiz summary CSV:', error)
    throw new Error('Failed to export quiz summary CSV file')
  }
}