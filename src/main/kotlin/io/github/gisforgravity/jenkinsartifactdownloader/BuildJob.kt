package io.github.gisforgravity.jenkinsartifactdownloader

import com.offbytwo.jenkins.model.Artifact
import com.offbytwo.jenkins.model.BuildWithDetails
import com.offbytwo.jenkins.model.JobWithDetails
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class BuildJob(
    private val job: JobWithDetails,
    private val artifactFileName: String?,
    private val downloadedBuild: Int
) {
    //constructor(job: JobWithDetails, downloadedBuild: Int) : this(job, null, downloadedBuild)

    fun newBuildRequired(): Boolean {
        val latestBuild = job.lastSuccessfulBuild
        return latestBuild.number > downloadedBuild
    }

    /**
     * Downloads the latest build
     * @return the build number
     */
    @Throws(IOException::class, InvalidArtifactException::class)
    fun downloadLatestBuild(outputDirectory: Path): BuildInfo {
        // Get the build details
        val newBuild = job.lastSuccessfulBuild.details()

        // Figure out which artifact is supposed to be downloaded
        val artifactToDownload: Artifact = if (artifactFileName == null) getDefaultArtifact(newBuild) else getArtifact(newBuild)

        // Where the artifact will be saved
        val artifactOutputPath = outputDirectory.resolve(artifactToDownload.fileName)
        newBuild.downloadArtifact(artifactToDownload).use { downloadStream ->
            Files.newOutputStream(artifactOutputPath).use { fileStream ->
                // Transfer from the download stream to the file
                downloadStream.transferTo(fileStream)
            }
        }
        return BuildInfo(newBuild.number, artifactToDownload.fileName)
    }

    @Throws(InvalidArtifactException::class)
    private fun getDefaultArtifact(build: BuildWithDetails): Artifact {
        val artifacts = build.artifacts
        if (artifacts.size != 1) throw InvalidArtifactException("There is more or less than 1 artifact. Please specify a specific artifact in the config.")
        return artifacts[0]
    }

    @Throws(InvalidArtifactException::class)
    private fun getArtifact(build: BuildWithDetails): Artifact {
        val artifacts = build.artifacts
        for (artifact in artifacts) {
            if (artifactFileName == artifact.fileName) return artifact
        }
        throw InvalidArtifactException("There is no artifact which matches the filename: $artifactFileName")
    }
}
