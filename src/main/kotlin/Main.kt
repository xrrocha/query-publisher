package querypublisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.FileInputStream
import java.io.InputStream

fun main(args: Array<String>) {
    val mapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())
    val webServer: WebServer =
        args
            .let {
                if (it.isEmpty()) "query-publisher.yml"
                else it[0]
            }
            .let(::FileInputStream)
            .let(mapper::readValue)
    webServer.start()
}

inline fun <reified T> ObjectMapper.readValue(inputStream: InputStream): T =
    readValue(inputStream, T::class.java)
