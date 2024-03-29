package ch.vilki.jfxldap.backend

object Errors {
    @JvmField
    var FILE_NOT_FOUND: String = "FILE_NOT_FOUND"

    var WINDOWS_SECURITY_NOT_SUPPORTED: String = "WINDOWS_SECURITY_NOT_SUPPORTED"
    @JvmField
    var WINDOWS_NOT_SECURED_WITH_CURRENT_USER: String = "WINDOWS_NOT_SECURED_WITH_CURRENT_USER"
    var PASSWORD_ERROR: String = " PASSWORD_ERROR"
    var STORAGE_NOT_SECURED: String = "STORAGE_NOT_SECURED"
    @JvmField
    var MASTER_PASSWORD_SECURED_ONLY: String = "MASTER_PASSWORD_SECURED_ONLY"
}
