package com.example.cachecleaner

import rikka.shizuku.Shizuku

/** Runs shell commands as the adb "shell" user through Shizuku. Call from a background thread. */
object ShizukuShell {
    fun exec(cmd: String): Pair<Int, String> = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor() to out.trim()
    } catch (t: Throwable) { -1 to (t.message ?: t.toString()) }

    fun clean(pkgs: List<String>, force: Boolean, cache: Boolean): Pair<Int, Int> {
        val failed = mutableSetOf<String>()
        val cacheMiss = mutableSetOf<String>()
        for (p in pkgs) {
            if (force && exec("am force-stop $p").first != 0) failed.add(p)
            if (cache && !exec("pm clear --cache-only $p").second.contains("Success", true)) cacheMiss.add(p)
        }
        if (cacheMiss.isNotEmpty() && exec("pm trim-caches 999G").first == 0) cacheMiss.clear()
        failed.addAll(cacheMiss)
        return (pkgs.size - failed.size) to failed.size
    }
}
