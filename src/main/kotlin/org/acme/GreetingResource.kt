package org.acme

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.acme.GreetingResource.Companion.HELLO
import java.nio.file.Paths

@Path("/$HELLO")
class GreetingResource(
    private val commandQueueService: CommandQueueService
) {
    @GET
    @Produces(MediaType.TEXT_HTML)
    fun hello() = createHTML().html {
        head {
            title { +"Quarkus HTML View" }
        }
        body {
            val record = commandQueueService.getCommandRcord()
            h1 { +"Execute yt-dlp Command" }
            form(action = "/$HELLO/$EXECUTE", method = FormMethod.post) {
                textInput(name = "command") {
                    placeholder = "Enter URL to download bestaudio..."
                }
                submitInput { value = "Queue Download" }
            }

            h2 { +"Queue Status" }
            h3 { +"Running Commands: ${record.runningCommands.size}" }
            ul {
                record.runningCommands.forEach { cmd ->
                    li { +"${cmd.urlStr} (ID: ${cmd.id})" }
                }
            }
            h3 { +"Pending Commands: ${record.pendingCommands.size}" }
            ul {
                record.pendingCommands.forEach { cmd ->
                    li { +"${cmd.urlStr} (ID: ${cmd.id})" }
                }
            }
            h3 { +"Finished Commands: ${record.finishedCommands.size}" }
            ul {
                record.finishedCommands.forEach { cmd ->
                    li {
                        +"${cmd.urlStr} - ${if (cmd.exitCode == 0) "Success" else "Failed"} (Time: ${cmd.executionTimeMs}ms)"
                        cmd.fileInfo?.fileName?.let { p { +"Filename: $it" } }
                        cmd.fileInfo?.fileLocation?.let { location ->
                            p {
                                a(href = "/$HELLO/$DOWNLOAD?file=$location", target = "_blank") {
                                    +"Download"
                                }
                            }
                        }
                        details {
                            summary { +"Details" }
                            p { +"Exit Code: ${cmd.exitCode}" }
                            p { +"Execution Time: ${cmd.executionTimeMs} ms" }
                            if (cmd.stdout.isNotBlank()) {
                                h4 { +"Standard Output:" }
                                pre { +cmd.stdout }
                            }
                            if (cmd.stderr.isNotBlank()) {
                                h4 { +"Standard Error:" }
                                pre { +cmd.stderr }
                            }
                        }
                    }
                }
            }
        }
    }

    @POST
    @Path("/$EXECUTE")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    fun executeCommand(@FormParam("command") command: String?): Response {
        if (command.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(
                    createHTML().html {
                        head { title { +"Error" } }
                        body {
                            h1 { +"Error" }
                            p { +"No command provided." }
                            a(href = "/$HELLO") { +"Back" }
                        }
                    }
                ).build()
        }

        commandQueueService.enqueue(command)
        return Response.seeOther(java.net.URI("/hello")).build()
    }

    @GET
    @Path("/$DOWNLOAD")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadFile(@jakarta.ws.rs.QueryParam("file") filePath: String?): Response {
        if (filePath.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("File parameter is required").build()
        }
        val file = Paths.get(filePath).toFile()
        if (!file.exists() || !file.isFile) {
            return Response.status(Response.Status.NOT_FOUND).entity("File not found").build()
        }

        return Response.ok(file)
            .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
            .build()
    }

    companion object {
        const val DOWNLOAD: String = "download"
        const val HELLO: String = "hello"
        const val EXECUTE: String = "execute"
    }

}