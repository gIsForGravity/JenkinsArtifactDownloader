package io.github.gisforgravity.jenkinsartifactdownloader

@JvmRecord
data class JobInfo(val server: String, val jobName: String, val fileName: String?)
