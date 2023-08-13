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
                // TODO Add 404 handler
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
                            </head>
                            <body>
                                ${pageHeader.htmlContent}
                                <hr>
                                <br>
                                ${
                                route.run(
                                    path,
                                    connection,
                                    // TODO Allow for multivalued params?
                                    ctx.queryParamMap().mapValues { (_, values) -> values.firstOrNull() })
                            }
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

class Route(sql: String, private val template: String) {
    companion object {
        private val ParameterNameRE = """:[_\p{IsLatin}][_\p{IsLatin}\d]+""".toRegex()

        private val classMapper = mapOf<String, (String) -> Any?>(
            "java.lang.Long" to java.lang.Long::parseLong,
            "java.lang.Short" to java.lang.Short::parseShort,
            "java.lang.Integer" to java.lang.Integer::parseInt,
            "java.lang.Float" to java.lang.Float::parseFloat,
            "java.lang.Double" to java.lang.Double::parseDouble,
            "java.sql.Date" to java.sql.Date::valueOf,
        )
    }

    private val paramSql = ParameterNameRE.replace(sql.trim(), "?")
    private val paramNames = ParameterNameRE
        .findAll(sql)
        .map(MatchResult::value)
        .withIndex()
        .map { (index, value) -> index + 1 to value.substring(1) }
        .toList()

    fun run(path: String, connection: Connection, params: Map<String, String?>) =
        generateSequence(
            connection
                .prepareStatement(paramSql)
                .also { ps ->
                    // TODO Cache prepared statement metadata
                    paramNames.forEach { (index, name) ->
                        val paramValue = params[name]?.let { value ->
                            val converter = classMapper[ps.metaData.getColumnClassName(index)] ?: { it }
                            converter(value)
                        }
                        if (paramValue != null) {
                            ps.setObject(index, paramValue)
                        } else {
                            ps.setNull(index, ps.metaData.getColumnType(index))
                        }
                    }
                }
                .executeQuery()
                .let { rs ->
                    val colNames = (1..rs.metaData.columnCount)
                        .map { index -> rs.metaData.getColumnLabel(index) }
                    Pair(rs, colNames)
                }
        ) { it }
            .takeWhile { (rs, _) -> rs.next() }
            .map { (rs, colNames) ->
                colNames.associate { colName ->
                    colName.lowercase() to rs.getObject(colName)
                }
            }
            .toList()
            .let { rows ->
                val model = params + Pair("rows", rows)
                // TODO Compile template at startup
                Pug4J.render(StringReader(template), path, model)!!
            }
}
