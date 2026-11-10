package com.focushome

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    private fun apps(vararg p: String) = p.map { AppItem(it) }

    @Test fun normalizeAddsMissingFavoritesAtTheEnd() {
        assertEquals(apps("b", "a", "c"), HomeLayout.normalize(apps("b", "a"), listOf("a", "b", "c")))
    }

    @Test fun normalizeDropsAppsThatAreNoLongerFavorites() {
        val layout = listOf(AppItem("a"), FolderItem("1", "Work", listOf("b", "x", "c")), AppItem("y"))
        assertEquals(
            listOf(AppItem("a"), FolderItem("1", "Work", listOf("b", "c"))),
            HomeLayout.normalize(layout, listOf("a", "b", "c")),
        )
    }

    @Test fun normalizeDissolvesFoldersWithOneAppAndRemovesEmptyOnes() {
        val layout = listOf(FolderItem("1", "One", listOf("a", "x")), FolderItem("2", "None", listOf("y")), AppItem("b"))
        assertEquals(apps("a", "b"), HomeLayout.normalize(layout, listOf("a", "b")))
    }

    @Test fun normalizeKeepsEachAppOnce() {
        val layout = listOf(AppItem("a"), FolderItem("1", "F", listOf("a", "b", "c")))
        assertEquals(
            listOf(AppItem("a"), FolderItem("1", "F", listOf("b", "c"))),
            HomeLayout.normalize(layout, listOf("a", "b", "c")),
        )
    }

    @Test fun moveReordersTopLevelItems() {
        assertEquals(apps("b", "c", "a"), HomeLayout.move(apps("a", "b", "c"), "a", 2))
        assertEquals(apps("c", "a", "b"), HomeLayout.move(apps("a", "b", "c"), "c", 0))
        assertEquals(apps("b", "c", "a"), HomeLayout.move(apps("a", "b", "c"), "a", 99))
    }

    @Test fun droppingAnAppOnAnAppMakesAFolderInTheTargetsPlace() {
        assertEquals(
            listOf(AppItem("a"), FolderItem("n", HomeLayout.DEFAULT_FOLDER_NAME, listOf("c", "b"))),
            HomeLayout.dropOnto(apps("a", "b", "c"), draggedKey = "b", targetKey = "c", newFolderId = "n"),
        )
    }

    @Test fun droppingAnAppOnAFolderAddsItToTheEnd() {
        val layout = listOf(FolderItem("1", "F", listOf("a", "b")), AppItem("c"))
        assertEquals(
            listOf(FolderItem("1", "F", listOf("a", "b", "c"))),
            HomeLayout.dropOnto(layout, "c", "folder:1", "n"),
        )
    }

    @Test fun foldersCantBeDroppedIntoThings() {
        val layout = listOf(FolderItem("1", "F", listOf("a", "b")), AppItem("c"))
        assertEquals(layout, HomeLayout.dropOnto(layout, "folder:1", "c", "n"))
    }

    @Test fun removingFromAFolderPutsTheAppRightAfterIt() {
        val layout = listOf(FolderItem("1", "F", listOf("a", "b", "c")), AppItem("d"))
        assertEquals(
            listOf(FolderItem("1", "F", listOf("a", "c")), AppItem("b"), AppItem("d")),
            HomeLayout.removeFromFolder(layout, "1", "b"),
        )
    }

    @Test fun removingTheSecondLastAppDissolvesTheFolder() {
        val layout = listOf(FolderItem("1", "F", listOf("a", "b")))
        assertEquals(apps("b", "a"), HomeLayout.removeFromFolder(layout, "1", "a"))
    }

    @Test fun moveInFolderAndRename() {
        val layout = listOf(FolderItem("1", "F", listOf("a", "b", "c")))
        assertEquals(listOf(FolderItem("1", "F", listOf("b", "c", "a"))), HomeLayout.moveInFolder(layout, "1", "a", 2))
        assertEquals(listOf(FolderItem("1", "Games", listOf("a", "b", "c"))), HomeLayout.renameFolder(layout, "1", "Games"))
    }

    @Test fun encodeDecodeRoundTripsAwkwardNames() {
        val layout = listOf(AppItem("a.b"), FolderItem("x1", "Work, play\t& more: 100%", listOf("c.d", "e.f")), AppItem("g"))
        assertEquals(layout, HomeLayout.decode(HomeLayout.encode(layout)))
    }

    @Test fun decodeIgnoresJunk() {
        assertEquals(apps("a"), HomeLayout.decode("a\ta\nnonsense\nf\tonly-two\n\n"))
    }
}
