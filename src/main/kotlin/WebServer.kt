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

class WebServer(
    private val port: Int = 1960,
    database: Database,
    pageHeader: PageHeader,
    assetsDir: String = ".",
    private val routes: Map<String, Route>
) {
    private val assetsDirectory = File(assetsDir)
        .also { require(it.isDirectory && it.canRead()) }
    private val rootPageContent = pageHeader.toHtml()
    private val connection = database.connect()

    fun start(): Javalin =
        Javalin
            .create { config ->
                config.staticFiles.add { staticFiles ->
                    staticFiles.hostedPath = "/"
                    staticFiles.directory = assetsDirectory.absolutePath
                    staticFiles.location = EXTERNAL
                }
            }
            .apply {
                get("/") { ctx -> ctx.html(rootPageContent) }
                routes.forEach { (path, route) ->
                    get("/$path") { ctx ->
                        ctx.html(route.run(connection, emptyMap()/*ctx.queryParamMap()*/))
                    }
                }
                start(port)
            }
}

enum class MarkupLanguage {
    MARKDOWN {
        private val flavour = CommonMarkFlavourDescriptor()
        private val compile = { pageHeader: String ->
            val parsedTree =
                MarkdownParser(flavour).buildMarkdownTreeFromString(pageHeader)
            HtmlGenerator(pageHeader, parsedTree, flavour).generateHtml()
        }

        override fun toHtml(source: String): String = compile(source)
    },
    PUG {
        override fun toHtml(source: String): String =
            Pug4J.render(StringReader(source), "", emptyMap())
    };

    abstract fun toHtml(source: String): String
}

class PageHeader(
    private val content: String,
    private val language: MarkupLanguage = MarkupLanguage.MARKDOWN,
) {
    fun toHtml() = language.toHtml(content)
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
