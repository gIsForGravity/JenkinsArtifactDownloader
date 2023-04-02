![jenkins project logo](jenkins-headshot.png)
# JenkinsArtifactDownloader
A plugin for Bukkit-based servers (Spigot, Paper, etc.) which
downloads the latest artifact from one or many Jenkins jobs

---
## Table of Contents
1. [JenkinsArtifactDownloader](#jenkinsartifactdownloader)
2. [What does this mean?](#what-does-this-mean)
3. [When should I use this?](#when-should-i-use-this)
4. [When shouldn't I use this?](#when-shouldnt-i-use-this)
5. [How to use](#how-to-use)
6. [Attribution](#attribution)

## What does this mean?
Jenkins is a CI (continuous integration) tool. One of its many
features is automatically building a maven or gradle (or even
ant) project after a git commit.

Combining Jenkins with this
plugin can **massively** simplify group development. No longer
do you have to manually build the plugin locally, stop your
server, delete the previous build of your plugin, upload the
new build, forget that you already uploaded the new build (did
I update it already or not? Better upload it again just to be
safe), and start the server.

Now, as long as you have a script to restart your server on
shutdown, all you have to do is git commit your local changes,
and type /stop in the console.

## When should I use this?

* ### For development
If you're developing in a group, and need to test features
together quickly, it will become much easier. As everyone
makes their changes, they can commit them and restart the
server, and see their changes instantly.

* ### For testing
Even if you're a lone developer, this tool can still make
testing easier. When you're testing with playtesters, you can
now apply a quick fix much faster, letting the testing
continue.

* ### In production... maybe?
In theory, you could have both a develop and production branch
on your git repo, and PR from your development branch to your
production branch once your changes have been tested

## When shouldn't I use this?

* ### For someone else's plugin
This plugin will allow you to watch other Jenkins servers and
automatically get new builds, but that doesn't mean its
recommended, **ESPECIALLY IN PRODUCTION**. In development,
it's probably okay.

## How to use
In JADL, all settings are configured in a `settings.toml` file
found within the plugin's folder.

There are two key parts of JADL: jobs, and servers. A
**server** is a jenkins server which contains jobs that you
would like to be updated on. A **job** is a job on a server
that you want to receive new builds for.

`Todo: Make that more readable`

You can define each server you would like in the `servers`
section of `settings.toml`. Each key represents a server,
with the value being the server's URL.
```toml
[servers]
# server name can be whatever you want
athion = "https://ci.athion.net"
citizens = "https://ci.citizensnpcs.co"
```
In the `jobs` section, you define the jenkins jobs that you
would like to automatically download. The key should represent
the job name, and should contain the property `server` and
optionally `artifact_file_name`
```toml
[jobs]
# artifact_file_name not needed because this job has only 1 artifact
HoloPlots = { server = "athion" }
Citizens2 = { server = "citizens" }
# job name can be in quotes - this can be useful for names with spaces
"FastAsyncWorldEdit" = { server = "athion", artifact_file_name = "FastAsyncWorldEdit-Bukkit-2.6.0-SNAPSHOT-396.jar" }
```
> `artifact_file_name` is only required if the job has more
> than one artifact

On server startup, JADL will check for updates to all builds
by comparing the newest build to the one in localdb.yml

If there are any issues inside `settings.toml`, JADL will let
you know with very user friendly (colorful!!) error messages
inspired by the Rust compiler.

## Contributing
Both issues and pull requests are very welcome. JADL is still
in a very early state, and any help is appreciated. Bugs are
to be expected, and bug reporting would be super helpful,
and feature requests would be nice too.

I especially expect issues with the error messages as I have
not tested them very well.

---
### Attribution
Mr. Jenkins is licensed under the licensed from 
[the Jenkins Project](https://jenkins.io) 
under the Creative Commons Attribution-ShareAlike 3.0
Unported License.