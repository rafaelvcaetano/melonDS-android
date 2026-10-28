package androidx.documentfile.provider

/** Keep real filesystem reads/creates; simulate only the provider refusing deletion. */
class DeleteDeniedDocumentFile(private val delegate: DocumentFile) : DocumentFile(null) {
    override fun createFile(mimeType: String, displayName: String) = delegate.createFile(mimeType, displayName)?.let(::DeleteDeniedDocumentFile)
    override fun createDirectory(displayName: String) = delegate.createDirectory(displayName)?.let(::DeleteDeniedDocumentFile)
    override fun getUri() = delegate.uri
    override fun getName() = delegate.name
    override fun getType() = delegate.type
    override fun isDirectory() = delegate.isDirectory
    override fun isFile() = delegate.isFile
    override fun isVirtual() = delegate.isVirtual
    override fun lastModified() = delegate.lastModified()
    override fun length() = delegate.length()
    override fun canRead() = delegate.canRead()
    override fun canWrite() = delegate.canWrite()
    override fun delete() = false
    override fun exists() = delegate.exists()
    override fun listFiles() = delegate.listFiles().map(::DeleteDeniedDocumentFile).toTypedArray()
    override fun renameTo(displayName: String) = delegate.renameTo(displayName)
}
