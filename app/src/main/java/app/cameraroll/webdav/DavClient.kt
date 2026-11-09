package app.cameraroll.webdav

import android.util.Xml
import app.cameraroll.R
import app.cameraroll.data.CloudConfig
import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser

data class DavEntry(
    val href: String,
    val isDir: Boolean,
    val contentLength: Long = 0,
    val contentType: String = "",
    val lastModified: String = "",
) {
    /** ruta relativa al root de ficheros del usuario, sin barra inicial */
    val relPath: String get() = href.removePrefix("/").let { it }
}

class DavException(message: String, val status: Int = 0) : IOException(message)

/** Cliente WebDAV mínimo (PROPFIND/PUT/GET/MKCOL/DELETE) para Nextcloud y OpenCloud. */
class DavClient(private val config: CloudConfig, private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val auth: String = Credentials.basic(config.username, config.password)
    private val root: String = config.davEndpoint

    private fun request(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", auth)

    private fun absUrl(relPath: String): String =
        root + "/" + relPath.trimStart('/').split('/').joinToString("/") {
            android.net.Uri.encode(it)
        }

    private fun checkOk(code: Int, op: String) {
        if (code !in 200..299) throw DavException(context.getString(R.string.dav_error_http, op, code), code)
    }

    /** Lista recursiva bajo [path] (relativa al root de ficheros del usuario). */
    fun listDeep(path: String): List<DavEntry> {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:prop>
                <d:getcontentlength/>
                <d:getcontenttype/>
                <d:getlastmodified/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

        val req = request(absUrl(path))
            .method("PROPFIND", body)
            .header("Depth", "infinity")
            .build()
        client.newCall(req).execute().use { res ->
            checkOk(res.code, "PROPFIND")
            val xml = res.body?.byteStream() ?: return emptyList()
            return parseMultistatus(xml, path)
        }
    }

    private fun parseMultistatus(stream: java.io.InputStream, basePath: String): List<DavEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(stream, "UTF-8")

        val entries = mutableListOf<DavEntry>()
        var href = ""
        var isDir = false
        var length = 0L
        var type = ""
        var lastMod = ""
        var inCollection = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "response" -> {
                        href = ""; isDir = false; length = 0; type = ""; lastMod = ""
                    }
                    "href" -> if (href.isEmpty()) href = parser.nextText()
                    "collection" -> inCollection = true
                    "getcontentlength" -> length = parser.nextText().toLongOrNull() ?: 0
                    "getcontenttype" -> type = parser.nextText()
                    "getlastmodified" -> lastMod = parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "collection" -> {
                        if (inCollection) isDir = true
                        inCollection = false
                    }
                    "response" -> {
                        if (href.isNotEmpty()) entries.add(DavEntry(href, isDir, length, type, lastMod))
                    }
                }
            }
            event = parser.next()
        }

        // normalizar: quitar prefijo del endpoint para obtener rutas relativas
        val prefix = android.net.Uri.parse(root).path ?: "/"
        return entries
            .filter { e ->
                val p = android.net.Uri.parse(e.href).path ?: e.href
                p.removePrefix(prefix).trim('/') != basePath.trim('/')
            }
            .map { e ->
                val p = android.net.Uri.parse(e.href).path ?: e.href
                e.copy(href = p.removePrefix(prefix).let { java.net.URLDecoder.decode(it, "UTF-8") })
            }
    }

    fun mkdirs(path: String) {
        var current = ""
        for (part in path.trim('/').split('/')) {
            if (part.isBlank()) continue
            current += "/$part"
            val req = request(absUrl(current)).method("MKCOL", null).build()
            client.newCall(req).execute().use { res ->
                // 201 creada, 405 ya existe
                if (res.code !in 200..299 && res.code != 405) {
                    throw DavException(context.getString(R.string.dav_error_mkcol, current, res.code), res.code)
                }
            }
        }
    }

    fun upload(relPath: String, file: File, mime: String) {
        val body = file.asRequestBody(mime.toMediaType())
        val req = request(absUrl(relPath)).put(body).build()
        client.newCall(req).execute().use { res -> checkOk(res.code, context.getString(R.string.dav_op_upload, relPath)) }
    }

    fun download(relPath: String, dest: File) {
        val req = request(absUrl(relPath)).get().build()
        client.newCall(req).execute().use { res ->
            checkOk(res.code, context.getString(R.string.dav_op_download, relPath))
            dest.parentFile?.mkdirs()
            res.body?.byteStream()?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: throw DavException(context.getString(R.string.dav_error_empty_download, relPath))
        }
    }

    fun delete(relPath: String) {
        val req = request(absUrl(relPath)).delete().build()
        client.newCall(req).execute().use { res ->
            if (res.code != 404) checkOk(res.code, context.getString(R.string.dav_op_delete, relPath))
        }
    }

    /** Prueba de conexión: PROPFIND de profundidad 0 sobre el root. */
    fun ping() {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
        val req = request(root).method("PROPFIND", body).header("Depth", "0").build()
        client.newCall(req).execute().use { res ->
            when (res.code) {
                in 200..299 -> Unit
                401, 403 -> throw DavException(context.getString(R.string.dav_error_bad_credentials, res.code), res.code)
                else -> throw DavException(context.getString(R.string.dav_error_server, res.code), res.code)
            }
        }
    }
}
