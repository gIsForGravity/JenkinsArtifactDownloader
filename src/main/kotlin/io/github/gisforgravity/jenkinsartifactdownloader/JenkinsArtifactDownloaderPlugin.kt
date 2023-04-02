package io.github.gisforgravity.jenkinsartifactdownloader

import com.offbytwo.jenkins.JenkinsServer
import net.md_5.bungee.api.ChatColor
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.tomlj.Toml
import org.tomlj.TomlParseError
import org.tomlj.TomlTable
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.logging.Level

class JenkinsArtifactDownloaderPlugin : JavaPlugin() {
    private val servers: MutableMap<String, JenkinsServer> = HashMap()
    private val jobs: MutableList<JobInfo> = ArrayList()
    override fun onLoad() {
        saveResource("settings.toml", false)
        logger.info("hi")
        try {
            // first loads the config so that the builds can be updated
            loadConfig()
            // check if any builds need updating and update them
            if (updateBuilds()) {
                printInfo("an artifact was updated. server requires restart")
                server.shutdown()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun loadConfig() {
        // Get the path to the settings.toml
        val settingsPath = dataFolder.toPath().resolve("settings.toml")
        // Parse settings.toml
        val parseResult = Toml.parse(settingsPath)

        // Print all the errors from it
        parseResult.errors().forEach(Consumer { error: TomlParseError -> logger.log(Level.SEVERE, error.toString()) })

        // Parse the servers and jobs sections
        if (parseResult.contains("servers") && parseResult.isTable("servers")) {
            parseResult.getTable("servers")?.let { parseServers(it) }
            printSuccess("registered " + servers.size + " servers")
        } else printError("there is no " + ChatColor.GOLD + "servers" + ChatColor.RESET + "property or it does not have subproperties")
        if (parseResult.contains("jobs") && parseResult.isTable("jobs")) {
            parseJobs(parseResult.getTable("jobs"))
            printSuccess("registered " + jobs.size + " jobs")
        } else printError("there is no " + ChatColor.GOLD + "jobs" + ChatColor.RESET + "property or it does not have subproperties")
    }

    private fun parseServers(serverMap: TomlTable) {
        for (serverName in serverMap.keySet()) {
            if (!serverMap.isString(serverName)) {
                printWarning("the property \"" + ChatColor.GOLD + serverName + ChatColor.RESET + "\" is under servers but it " + ChatColor.RED + "is not" + ChatColor.RESET + " a " + ChatColor.LIGHT_PURPLE + "string" + ChatColor.RESET + ". will be skipped.")
                continue
            }

            // The key is the server name, and the val is the url
            val serverAddress = serverMap.getString(serverName)!!

            // Attempt to add the server to the list and explain why we can't otherwise
            try {
                val server = JenkinsServer(URI(serverAddress))
                servers[serverName] = server
            } catch (e: URISyntaxException) {
                // There was a problem with the formatting of the url
                printError("the url address is invalid for server: $serverName")
            }
        }
    }

    private fun parseJobs(jobMap: TomlTable?) {
        for (job in jobMap!!.keySet()) {
            if (!jobMap.isTable(job)) {
                printWarning("\"$job\" is a key under jobs but does not have properties. will be skipped")
                continue
            }
            parseJob(job, jobMap.getTable(job))
        }
    }

    /**
     * Parses a toml table representing a job
     * Precondition: job is a toml table and not null
     * @param jobName the key in settings.toml
     * @param job the table which represents this job
     */
    private fun parseJob(jobName: String, job: TomlTable?) {
        // Check if it has a server tag
        if (!job!!.contains("server")) {
            printError("job \"" + ChatColor.GOLD + jobName + ChatColor.RESET + "\" " + ChatColor.RED + "does not" + ChatColor.RESET + "have a " + ChatColor.LIGHT_PURPLE + "server" + ChatColor.RESET + " property")
            return
        } else if (!job.isString("server")) {
            printError("the server property for job \"" + ChatColor.GOLD + jobName + ChatColor.RESET + "\" " + ChatColor.RED + "is not" + ChatColor.RESET + " a " + ChatColor.LIGHT_PURPLE + " string")
            return
        }
        val serverName = job.getString("server")

        // Check if the server name is in the list provided
        var exists = false
        for (server in servers.keys) if (server == serverName) {
            exists = true
            break
        }
        if (!exists) {
            printError("job \"" + ChatColor.GOLD + jobName + ChatColor.RESET + "\" is on the server \"" + ChatColor.GOLD + serverName + ChatColor.RESET + "\", but no such server exists")
            return
        }

        // Check if it has an artifactFileName tag
        val artifactFileName: String? = if (job.contains("artifact_file_name") && !job.isString("artifact_file_name")) {
            printWarning("job \"" + ChatColor.GOLD + jobName + ChatColor.RESET + "\" has a " + ChatColor.LIGHT_PURPLE + "artifact_file_name" + ChatColor.RESET + " property, but it " + ChatColor.RED + "is not" + ChatColor.RESET + " a " + ChatColor.LIGHT_PURPLE + "string. the property will be ignored")
            null
        } else if (job.contains("artifact_file_name")) job.getString("artifact_file_name") else null

        // Add the job to the list
        jobs.add(JobInfo(serverName!!, jobName, artifactFileName))
    }

    @Throws(IOException::class)
    private fun updateBuilds(): Boolean {
        val localBuildDb = YamlConfiguration()
        val yamlFilePath = dataFolder.toPath().resolve("builddb.yml")
        try {
            if (Files.exists(yamlFilePath) && Files.isRegularFile(yamlFilePath)) localBuildDb.load(yamlFilePath.toFile())
        } catch (e: InvalidConfigurationException) {
            printWarning(ChatColor.GOLD.toString() + "builddb.yml" + ChatColor.RESET + " is " + ChatColor.RED + "formatted incorrectly" + ChatColor.RESET + ". will be reset.")
            Files.delete(yamlFilePath)
        }

        // Set every job's latest version to -1 if there
        // isn't one already
        for ((_, jobName) in jobs) {
            localBuildDb.addDefault(jobName, -1)
        }

        // Where the artifacts will be output
        val outputFolder = dataFolder.toPath().parent.resolve("update")
        Files.createDirectories(outputFolder)

        // If this bool is marked as true,
        // then we updated something, and we
        // need to restart the server
        var anythingUpdated = false

        // Loop through all jobs, check them, and update if necessary
        for ((server1, jobName, fileName) in jobs) {
            printInfo("checking job: " + ChatColor.GOLD + jobName)
            val server = servers[server1]
            val detailedJob = server!!.getJob(jobName)
            // If the job did not exist on the server, error
            if (detailedJob == null) {
                printError("job \"" + ChatColor.GOLD + jobName + ChatColor.RESET + "\" " + ChatColor.RED + "cannot be found" + ChatColor.RESET + " on the server \"" + ChatColor.GOLD + server1)
                continue
            }

            // Create a buildJob to manage downloading it and stuff
            val buildJob = BuildJob(detailedJob, fileName, localBuildDb.getInt(jobName))
            // Skip this if we don't need a new build
            if (!buildJob.newBuildRequired()) continue
            printInfo("downloading job: " + ChatColor.GOLD + jobName)
            anythingUpdated = true
            try {
                // Attempt to download build, if it works
                // then save the new build number to a conf
                // file
                val (buildNumber, fileName1) = buildJob.downloadLatestBuild(outputFolder)
                localBuildDb[jobName] = buildNumber
                localBuildDb.save(yamlFilePath.toFile())
                if (findPluginOutputDir(fileName1) != outputFolder) Files.move(
                    outputFolder.resolve(
                        fileName1
                    ), findPluginOutputDir(fileName1).resolve(fileName1)
                )
                printSuccess("downloaded build " + ChatColor.LIGHT_PURPLE + "#" + buildNumber + ChatColor.RESET + " of job " + ChatColor.GOLD + jobName)
            } catch (e: InvalidArtifactException) {
                printError("error while downloading job" + ChatColor.GOLD + jobName + ChatColor.RED + e.message)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        // Tells server if a restart is necessary
        return anythingUpdated
    }

    private fun pluginAlreadyExists(fileName: String): Boolean {
        val pluginPath = dataFolder.toPath().parent.resolve(fileName)
        return Files.exists(pluginPath) && Files.isRegularFile(pluginPath)
    }

    private fun findPluginOutputDir(fileName: String): Path {
        return if (pluginAlreadyExists(fileName)) dataFolder.toPath().parent.resolve("update") else dataFolder.toPath().parent
    }

    private fun printError(error: String) {
        val logMessage = "[" +
                name +
                "] " +
                ChatColor.RED +
                ChatColor.BOLD +
                "error" +
                ChatColor.RESET +
                ": " +
                error
        server.consoleSender.sendMessage(logMessage)
    }

    private fun printWarning(error: String) {
        val logMessage = "[" +
                name +
                "] " +
                ChatColor.YELLOW +
                ChatColor.BOLD +
                "warning" +
                ChatColor.RESET +
                ": " +
                error
        server.consoleSender.sendMessage(logMessage)
    }

    private fun printSuccess(error: String) {
        val logMessage = "[" +
                name +
                "] " +
                ChatColor.DARK_GREEN +
                ChatColor.BOLD +
                "success" +
                ChatColor.RESET +
                ": " +
                error
        server.consoleSender.sendMessage(logMessage)
    }

    private fun printInfo(message: String) {
        val logMessage = "[" +
                name +
                "] " +
                ChatColor.BOLD +
                "info" +
                ChatColor.RESET +
                ": " +
                message
        server.consoleSender.sendMessage(logMessage)
    }

    override fun onDisable() {
        for (server in servers.values) {
            server.close()
        }
    }
}
