/** Hands a downloaded document to the browser and releases the object URL right away. */
export function saveFile(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.rel = 'noopener'
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function pdfFilename(quoteNumber: string) {
  return `Angebot-${quoteNumber.replace(/[^A-Za-z0-9-]/g, '')}.pdf`
}
