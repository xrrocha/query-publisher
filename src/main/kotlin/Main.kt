package querypublisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.FileInputStream

fun main(args: Array<String>) {
    try {
        val configFilename =
            if (args.isNotEmpty()) args[0]
            else "query-publisher.yml"
        val configIs = FileInputStream(configFilename)
        val queryPublisher: QueryPublisher =
            ObjectMapper(YAMLFactory())
                .registerModule(
                    KotlinModule.Builder()
                        .build()
                )
                .readValue(configIs, QueryPublisher::class.java)
        queryPublisher.start()
        Runtime.getRuntime().addShutdownHook(Thread(queryPublisher::stop))
    } catch (e: Exception) {
        System.err.println("Error creating server: ${e.message}")
    }
}