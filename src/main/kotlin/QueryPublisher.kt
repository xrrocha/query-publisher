package querypublisher

import de.neuland.pug4j.Pug4J
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location.EXTERNAL
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.io.File
import java.io.StringReader
import java.sql.Connection
import java.sql.DriverManager

class QueryPublisher(
    private val port: Int = 1960,
    database: Database,
    pageHeader: PageHeader,
    assetsDir: String = ".",
    routes: Map<String, Route>
) {
    private val assetsDirectory = File(assetsDir)
        .also { require(it.isDirectory && it.canRead()) }
    private val connection = database.connect()

    private val javalin =
        Javalin
            .create { config ->
                config.staticFiles.add { staticFiles ->
                    staticFiles.hostedPath = "/"
                    staticFiles.directory = assetsDirectory.absolutePath
                    staticFiles.location = EXTERNAL
                }
            }
            .apply {
                get("/") { ctx -> ctx.html(pageHeader.htmlContent) }
                routes.forEach { (path, route) ->
                    get("/$path") { ctx ->
                        ctx.html(
                            """
                            <html>
                            <head>
                                <title>${pageHeader.title}</title>
                                <meta charset="utf-8">
                            </head>
                            <body>
                                ${pageHeader.htmlContent}
                                <hr>
                                <br>
                                ${route.run(connection, emptyMap()/*ctx.queryParamMap()*/)}
                            </body>
                            </html>
                            """.trimIndent()
                        )
                    }
                }
            }


    fun start() {
        javalin.start(port)
    }

    fun stop() {
        javalin.stop()
    }
}

enum class MarkupLanguage {
    markdown {
        private val flavour = CommonMarkFlavourDescriptor()
        private val compile = { pageHeader: String ->
            val parsedTree =
                MarkdownParser(flavour).buildMarkdownTreeFromString(pageHeader)
            HtmlGenerator(pageHeader, parsedTree, flavour)
                .generateHtml()
                .removeSurrounding("<body>", "</body>")
        }

        override fun toHtml(source: String): String = compile(source)
    },
    pug {
        override fun toHtml(source: String): String =
            Pug4J.render(StringReader(source), "", emptyMap())
    },
    html {
        override fun toHtml(source: String): String = source
    };

    abstract fun toHtml(source: String): String
}

class PageHeader(
    val title: String,
    content: String,
    language: MarkupLanguage = MarkupLanguage.pug,
) {
    val htmlContent = language.toHtml(content)
}

class Database(
    private val className: String,
    private val url: String,
    private val user: String,
    private val password: String?,
    private val initScript: String?
) {
    fun connect(): Connection =
        Class.forName(className)
            .let { DriverManager.getConnection(url, user, password) }
            .also { connection ->
                initScript
                    ?.split(";\\s*\n".toRegex())
                    ?.forEach { sql ->
                        connection
                            .createStatement()
                            .execute(sql)
                    }

            }
}

class Route(private val sql: String, private val template: String) {
    fun run(connection: Connection, params: Map<String, Any?>) =
        generateSequence(connection.createStatement().executeQuery(sql)) { it }
            .takeWhile { it.next() }
            .map { rs ->
                (1..rs.metaData.columnCount).associate { colIdx ->
                    rs.metaData.getColumnLabel(colIdx).lowercase() to rs.getObject(colIdx)
                }
            }
            .toList()
            .let { rows ->
                val model = params + mapOf("rows" to rows)
                Pug4J.render(StringReader(template), "", model)!!
            }
}
