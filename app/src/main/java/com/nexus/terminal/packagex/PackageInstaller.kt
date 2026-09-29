package com.nexus.terminal.packagex
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
class PackageInstaller(private val bin:File){
 fun install(pkg:PackageInfo):Result<File>{return runCatching{val tmp=File.createTempFile("pkg-",".zip",bin);val c=URL(pkg.archiveUrl).openConnection() as HttpURLConnection;c.connectTimeout=10000;c.readTimeout=30000;c.inputStream.use{it.copyTo(tmp.outputStream())};c.disconnect();pkg.sha256?.let{check(hash(tmp)==it.lowercase()){"SHA-256 mismatch"}};ZipInputStream(tmp.inputStream()).use{z->var e=z.nextEntry;while(e!=null){val out=File(bin,e.name).canonicalFile;check(out.path.startsWith(bin.canonicalPath+File.separator)){"Invalid archive path"};if(e.isDirectory)out.mkdirs() else {out.parentFile?.mkdirs();out.outputStream().use{z.copyTo(it)}};e=z.nextEntry}};tmp.delete();File(bin,pkg.name)}}
 private fun hash(f:File)=MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString(""){"%02x".format(it)}
}
