package ch.vilki.jfxldap.backend

interface IProgress {
    fun setProgress(progress: Double, description: String?)
    fun signalTaskDone(taskName: String?, description: String?, e: Exception?)
    fun setProgress(taskName: String?, progress: Double)
}
