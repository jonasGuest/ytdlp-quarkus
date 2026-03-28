package org.acme

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import java.time.Instant
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.net.URI

sealed interface Command {
    val id: UUID
    val urlStr: String
    val createdAt: Instant
}

data class NewCommand(
    override val id: UUID,
    override val urlStr: String,
    override val createdAt: Instant
) : Command

fun createNewCommand(urlStr: String): NewCommand {
    val id = UUID.randomUUID()
    return NewCommand(id, urlStr, Instant.now())
}

data class FileInfo(
    val fileName: String,
    val fileLocation: String
)
data class FinishedCommand(
    override val id: UUID,
    override val urlStr: String,
    override val createdAt: Instant,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val executionTimeMs: Long,
    val fileInfo: FileInfo?
) : Command

data class CommandRecord(
    val pendingCommands: List<NewCommand>,
    val runningCommands: List<Command>,
    val finishedCommands: List<FinishedCommand>
)

fun createFinishedCommand(
    command: NewCommand,
    stdout: String,
    stderr: String,
    exitCode: Int,
    executionTimeMs: Long,
    fileInfo: FileInfo?,
): FinishedCommand {
    return FinishedCommand(
        command.id,
        command.urlStr,
        command.createdAt,
        stdout,
        stderr,
        exitCode,
        executionTimeMs,
        fileInfo,
    )
}

fun downloadYtDlp(): Path {
    val ytDlpPath = Files.createTempFile("yt-dlp", ".exe")
    ytDlpPath.toFile().deleteOnExit()
    println("Downloading yt-dlp to ${ytDlpPath.toAbsolutePath()}...")

    URI.create("https://github.com/yt-dlp/yt-dlp/releases/download/2026.03.03/yt-dlp").toURL().openStream()
        .use { input ->
            Files.copy(input, ytDlpPath, StandardCopyOption.REPLACE_EXISTING)
        }

    val perms = Files.getPosixFilePermissions(ytDlpPath).toMutableSet()
    perms.add(PosixFilePermission.OWNER_EXECUTE)
    Files.setPosixFilePermissions(ytDlpPath, perms)

    println("yt-dlp downloaded and made executable at $ytDlpPath")

    return ytDlpPath.toAbsolutePath()
}

@ApplicationScoped
class CommandQueueService {
    private val pendingCommands = LinkedBlockingQueue<NewCommand>()
    private val runningCommands = ConcurrentHashMap<UUID, Command>()
    private val finishedCommands = ConcurrentLinkedQueue<FinishedCommand>()
    private val executor = Executors.newSingleThreadExecutor()
    private val ytDlpPath = downloadYtDlp()

    init {
        executor.submit { processQueue() }
    }

    fun enqueue(commandStr: String): String {
        val command = createNewCommand(commandStr)
        pendingCommands.offer(command)
        return command.id.toString()
    }


    fun getCommandRcord(): CommandRecord {
        return CommandRecord(
            pendingCommands.toList(),
            runningCommands.values.toList(),
            finishedCommands.toList()
        )
    }

    private fun processQueue() {
        while (!Thread.currentThread().isInterrupted) {
            val command = pendingCommands.take()
            try {
                executeCommand(command)
            } catch (e: Exception) {
                logger.error("Error processing command ${command.id}: ${e.message}", e)
            }
        }
    }

    private fun executeCommand(command: NewCommand) {
        runningCommands[command.id] = command

        val startTimeMillis = System.currentTimeMillis()
        val urlToDownload = command.urlStr.trim()
        require(urlToDownload.isNotBlank()) { "URL cannot be blank" }
        val tempDir = Files.createTempDirectory("yt-dlp-downloads")

        val process = getDownloadCommand(tempDir, urlToDownload).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()
        val exitCode = process.exitValue()

        val downloadedFiles = Files.list(tempDir).filter { Files.isRegularFile(it) }.toList()
        val fileInfo = if (exitCode == 0 && downloadedFiles.isNotEmpty()) {
                val downloadedFile = downloadedFiles[0]
                FileInfo(downloadedFile.fileName.toString(), fileLocation = downloadedFile.toAbsolutePath().toString())
        } else {
            logger.warn("Command ${command.id} failed with exit code $exitCode. No file downloaded.")
            null
        }
        val executionTimeMs = System.currentTimeMillis() - startTimeMillis

        val finished = createFinishedCommand(command, stdout, stderr, exitCode, executionTimeMs, fileInfo)
        runningCommands.remove(command.id)
        finishedCommands.add(finished)
    }

    private fun getDownloadCommand(tempDir: Path, urlToDownload: String): ProcessBuilder = ProcessBuilder(
        ytDlpPath.toString(),
        "-f", "bestaudio",
        "-o", "${tempDir.toAbsolutePath()}/%(title)s.%(ext)s",
        urlToDownload
    )

    companion object {
        // set logger
        val logger: Logger = Logger.getLogger(CommandQueueService::class.java)
    }
}
