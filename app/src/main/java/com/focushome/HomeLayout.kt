package com.focushome

import java.net.URLDecoder
import java.net.URLEncoder

/** Something on the home grid: a single favorite app, or a folder of them. */
sealed interface HomeItem {
    /** Stable identity for drag and drop. Package name for apps, "folder:<id>" for folders. */
    val key: String
}

data class AppItem(val pkg: String) : HomeItem {
    override val key get() = pkg
}

data class FolderItem(val id: String, val name: String, val apps: List<String>) : HomeItem {
    override val key get() = "folder:$id"
}

/**
 * How the favorites are arranged on the home screen. The essentials list stays the single answer to
 * "which apps may open in Focus mode"; the layout only orders and groups them, and is kept in step:
 * apps that stop being favorites drop out, new favorites are added at the end.
 */
object HomeLayout {
    const val DEFAULT_FOLDER_NAME = "Folder"

    /** Fixes up a layout so it holds exactly [essentials], each once, with no folders under two apps. */
    fun normalize(items: List<HomeItem>, essentials: List<String>): List<HomeItem> {
        val allowed = essentials.toSet()
        val seen = HashSet<String>()
        val out = ArrayList<HomeItem>()
        for (item in items) {
            when (item) {
                is AppItem -> if (item.pkg in allowed && seen.add(item.pkg)) out += item
                is FolderItem -> {
                    val apps = item.apps.filter { it in allowed && seen.add(it) }
                    when (apps.size) {
                        0 -> Unit
                        1 -> out += AppItem(apps[0])
                        else -> out += item.copy(apps = apps)
                    }
                }
            }
        }
        essentials.filter { seen.add(it) }.forEach { out += AppItem(it) }
        return out
    }

    /** Moves the top-level item [key] to [toIndex]. */
    fun move(items: List<HomeItem>, key: String, toIndex: Int): List<HomeItem> {
        val from = items.indexOfFirst { it.key == key }
        if (from < 0) return items
        val list = items.toMutableList()
        val item = list.removeAt(from)
        list.add(toIndex.coerceIn(0, list.size), item)
        return list
    }

    /**
     * Drops the app [draggedKey] onto [targetKey]: onto an app it makes a new folder in the target's
     * place; onto a folder it joins the end of that folder. Anything else leaves the layout alone.
     */
    fun dropOnto(items: List<HomeItem>, draggedKey: String, targetKey: String, newFolderId: String): List<HomeItem> {
        if (draggedKey == targetKey) return items
        val dragged = items.firstOrNull { it.key == draggedKey } as? AppItem ?: return items
        val target = items.firstOrNull { it.key == targetKey } ?: return items
        val replacement = when (target) {
            is AppItem -> FolderItem(newFolderId, DEFAULT_FOLDER_NAME, listOf(target.pkg, dragged.pkg))
            is FolderItem -> target.copy(apps = target.apps + dragged.pkg)
        }
        return items.mapNotNull {
            when (it.key) {
                draggedKey -> null
                targetKey -> replacement
                else -> it
            }
        }
    }

    fun renameFolder(items: List<HomeItem>, id: String, name: String): List<HomeItem> =
        items.map { if (it is FolderItem && it.id == id) it.copy(name = name) else it }

    /** Moves [pkg] within folder [id] to [toIndex]. */
    fun moveInFolder(items: List<HomeItem>, id: String, pkg: String, toIndex: Int): List<HomeItem> = items.map {
        if (it is FolderItem && it.id == id && pkg in it.apps) {
            val apps = it.apps.toMutableList()
            apps.remove(pkg)
            apps.add(toIndex.coerceIn(0, apps.size), pkg)
            it.copy(apps = apps)
        } else {
            it
        }
    }

    /** Takes [pkg] out of folder [id] and puts it on the home grid just after the folder. */
    fun removeFromFolder(items: List<HomeItem>, id: String, pkg: String): List<HomeItem> {
        val index = items.indexOfFirst { it is FolderItem && it.id == id }
        if (index < 0) return items
        val folder = items[index] as FolderItem
        if (pkg !in folder.apps) return items
        val list = items.toMutableList()
        val rest = folder.apps - pkg
        list[index] = if (rest.size == 1) AppItem(rest[0]) else folder.copy(apps = rest)
        list.add(index + 1, AppItem(pkg))
        return list
    }

    // One item per line: "a<TAB>pkg" or "f<TAB>id<TAB>url-encoded name<TAB>pkg,pkg".
    fun encode(items: List<HomeItem>): String = items.joinToString("\n") {
        when (it) {
            is AppItem -> "a\t${it.pkg}"
            is FolderItem -> "f\t${it.id}\t${URLEncoder.encode(it.name, "UTF-8")}\t${it.apps.joinToString(",")}"
        }
    }

    fun decode(text: String): List<HomeItem> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        when {
            parts.size == 2 && parts[0] == "a" && parts[1].isNotBlank() -> AppItem(parts[1])
            parts.size == 4 && parts[0] == "f" -> FolderItem(
                id = parts[1],
                name = runCatching { URLDecoder.decode(parts[2], "UTF-8") }.getOrDefault(DEFAULT_FOLDER_NAME),
                apps = parts[3].split(',').filter { it.isNotBlank() },
            )
            else -> null
        }
    }.toList()
}
