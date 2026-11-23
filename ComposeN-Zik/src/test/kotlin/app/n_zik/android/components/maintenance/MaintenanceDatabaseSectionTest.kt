package app.n_zik.android.components.maintenance

import java.util.Locale

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import app.n_zik.android.R
import app.n_zik.android.core.maintenance.DbCleanupLinkRecord
import app.n_zik.android.core.maintenance.DedupGroupRecord
import app.n_zik.android.core.maintenance.DedupGroupStatus
import app.n_zik.android.core.maintenance.DownloadRevalidationRecord
import app.n_zik.android.core.maintenance.MaintenanceConvergenceState
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.core.maintenance.MaintenanceDownloadRevalidationState
import app.n_zik.android.core.maintenance.MaintenanceDownloadSelfHealState
import app.n_zik.android.core.migration.ConvergenceSummary
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MaintenanceDatabaseSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Test
    fun `database passes become expandable when the snapshot is ready`() {
        val passLabels = listOf(
            R.string.maintenance_dedup,
            R.string.maintenance_convergence,
            R.string.maintenance_db_cleanup,
            R.string.maintenance_download_revalidation,
            R.string.maintenance_download_self_heal,
        ).map { context.getString(it) }
        val loadFailed = context.getString(R.string.common_load_failed)
        val noData = context.getString(R.string.maintenance_no_data)
        val clean = context.getString(R.string.maintenance_pass_clean)
        val cleaned = context.resources.getQuantityString(R.plurals.maintenance_pass_cleaned_count, 1, 1)
        val initialSnapshot = MaintenanceSnapshot(
            convergence = MaintenanceConvergenceState(
                timestamp = 1L,
                summary = ConvergenceSummary(
                    changed = 2,
                    unbackedSongs = 0,
                    unlinkedSongs = 0,
                    customSongs = 0,
                    unlinkedAlbums = 0,
                    inconsistentAlbums = 0,
                    countMismatchAlbums = 0,
                    customAlbums = 0,
                ),
            ),
            dbCleanup = MaintenanceDbCleanupState(
                timestamp = 1L,
                removed = 1,
                removedLinks = listOf(DbCleanupLinkRecord("Song title", "Removed artist")),
            ),
            downloadRevalidation = MaintenanceDownloadRevalidationState(timestamp = 1L, removed = 0),
            downloadSelfHeal = MaintenanceDownloadSelfHealState(
                timestamp = 1L,
                mediaId = "healed-media-id",
                title = "Healed track",
            ),
        )
        var snapshot by mutableStateOf(initialSnapshot)
        var readPhase by mutableStateOf(MaintenanceReadPhase.Pending)
        val sheetStatusColor = Color.Cyan
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                var dedupExpanded by remember { mutableStateOf(false) }
                var convergenceExpanded by remember { mutableStateOf(false) }
                var dbCleanupExpanded by remember { mutableStateOf(false) }
                var downloadRevalidationExpanded by remember { mutableStateOf(false) }
                var downloadSelfHealExpanded by remember { mutableStateOf(false) }
                Column {
                    MaintenanceDatabaseSection(
                        snapshot = snapshot.takeIf { readPhase == MaintenanceReadPhase.Ready },
                        readPhase = readPhase,
                        statusColumnWidth = 110.dp,
                        locale = Locale.US,
                        sheetStatusColor = sheetStatusColor,
                        dedupExpanded = dedupExpanded,
                        onToggleDedup = { dedupExpanded = !dedupExpanded },
                        convergenceExpanded = convergenceExpanded,
                        onToggleConvergence = { convergenceExpanded = !convergenceExpanded },
                        dbCleanupExpanded = dbCleanupExpanded,
                        onToggleDbCleanup = { dbCleanupExpanded = !dbCleanupExpanded },
                        downloadRevalidationExpanded = downloadRevalidationExpanded,
                        onToggleDownloadRevalidation = { downloadRevalidationExpanded = !downloadRevalidationExpanded },
                        downloadSelfHealExpanded = downloadSelfHealExpanded,
                        onToggleDownloadSelfHeal = { downloadSelfHealExpanded = !downloadSelfHealExpanded },
                    )
                }
            }
        }

        for (label in passLabels) composeRule.onNodeWithText(label).assertIsNotEnabled()
        composeRule.onAllNodesWithText(noData).assertCountEquals(0)
        composeRule.onAllNodesWithText(loadFailed).assertCountEquals(0)

        composeRule.runOnIdle { readPhase = MaintenanceReadPhase.Unavailable }
        for (label in passLabels) composeRule.onNodeWithText(label).assertIsNotEnabled()
        composeRule.onAllNodesWithText(loadFailed).assertCountEquals(passLabels.size)

        composeRule.runOnIdle { readPhase = MaintenanceReadPhase.Ready }
        for (label in passLabels) composeRule.onNodeWithText(label).assertIsEnabled()
        composeRule.onAllNodesWithText(noData).assertCountEquals(1)

        val dedup = composeRule.onNodeWithText(context.getString(R.string.maintenance_dedup))
        dedup.performClick()
        composeRule.onAllNodesWithText(noData).assertCountEquals(2)

        val revalidation = composeRule.onNodeWithText(context.getString(R.string.maintenance_download_revalidation))
        revalidation.performClick()
        composeRule.onAllNodesWithText(clean).assertCountEquals(1)

        val cleanup = composeRule.onNodeWithText(context.getString(R.string.maintenance_db_cleanup))
        cleanup.performClick()
        composeRule.onNodeWithText("Song title").assertExists()
        composeRule.onNodeWithText("Removed artist").assertExists()
        composeRule.onAllNodesWithText(cleaned).assertCountEquals(1)
        cleanup.performClick()
        composeRule.onNodeWithText("Song title").assertDoesNotExist()
        composeRule.onAllNodesWithText(cleaned).assertCountEquals(0)

        composeRule.onNodeWithText(context.getString(R.string.maintenance_convergence)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.maintenance_convergence_copies_rewritten, 2)).assertExists()
        val twoCleaned = context.resources.getQuantityString(R.plurals.maintenance_pass_cleaned_count, 2, 2)
        composeRule.onAllNodesWithText(twoCleaned).assertCountEquals(1)

        composeRule.onNodeWithText(context.getString(R.string.maintenance_download_self_heal)).performClick()
        composeRule.onNodeWithText("Healed track").assertExists()
        composeRule.onNodeWithText("healed-media-id").assertExists()
        composeRule.onAllNodesWithText(cleaned).assertCountEquals(1)

        val cleanupTime = 86_400_000L
        val revalidationTime = 172_800_000L
        composeRule.runOnIdle {
            snapshot = initialSnapshot.copy(
                dedup = MaintenanceDedupState(
                    timestamp = 1L,
                    groups = 2,
                    resolved = 1,
                    songResolved = 0,
                    merged = 2,
                    flagged = 3,
                    skipped = 0,
                    deferred = 1,
                    localKept = 0,
                    records = listOf(
                        DedupGroupRecord("Zulu", DedupGroupStatus.DEFERRED),
                        DedupGroupRecord("Alpha", DedupGroupStatus.RESOLVED, mergedRows = 2, flaggedRows = 3),
                    ),
                ),
                dbCleanup = MaintenanceDbCleanupState(timestamp = cleanupTime, removed = 2),
                downloadRevalidation = MaintenanceDownloadRevalidationState(
                    timestamp = revalidationTime,
                    removed = 3,
                    removedTracks = listOf(DownloadRevalidationRecord("removed-media-id", "Removed download")),
                ),
            )
        }
        val alpha = composeRule.onNodeWithText("Alpha").fetchSemanticsNode()
        val zulu = composeRule.onNodeWithText("Zulu").fetchSemanticsNode()
        assertTrue(alpha.positionInRoot.y < zulu.positionInRoot.y)
        val resources = context.resources
        val merged = resources.getQuantityString(R.plurals.maintenance_dedup_merged_count, 2, 2)
        val flagged = resources.getQuantityString(R.plurals.maintenance_dedup_flagged_count, 3, 3)
        val counts = context.getString(R.string.maintenance_dedup_counts_format, merged, flagged)
        val resolved = context.getString(R.string.maintenance_status_resolved)
        composeRule.onNodeWithText("$resolved: $counts").assertExists()

        composeRule.onNodeWithText("Removed download").assertExists()
        composeRule.onNodeWithText("removed-media-id").assertExists()
        revalidation.performClick()
        val threeCleaned = resources.getQuantityString(R.plurals.maintenance_pass_cleaned_count, 3, 3)
        val cleanupStatus = "${formatTimestamp(cleanupTime, Locale.US)} · $twoCleaned"
        val revalidationStatus = "${formatTimestamp(revalidationTime, Locale.US)} · $threeCleaned"
        val cleanupLayout = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(cleanupStatus, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(cleanupLayout) }
        assertEquals(sheetStatusColor, cleanupLayout.single().layoutInput.style.color)
        composeRule.onNodeWithText(revalidationStatus).assertExists()

        composeRule.runOnIdle {
            snapshot = snapshot.copy(
                downloadRevalidation = MaintenanceDownloadRevalidationState(timestamp = revalidationTime, removed = 3),
            )
        }
        composeRule.onAllNodesWithText(twoCleaned).assertCountEquals(1)
        cleanup.performClick()
        composeRule.onAllNodesWithText(twoCleaned).assertCountEquals(2)
        revalidation.performClick()
        composeRule.onAllNodesWithText(threeCleaned).assertCountEquals(1)
    }

    @Test
    fun `sheet retains the expanded report on refresh failure and reloads on retry`() {
        val firstSnapshot = MaintenanceSnapshot(
            dbCleanup = MaintenanceDbCleanupState(
                timestamp = 1L,
                removed = 1,
                removedLinks = listOf(DbCleanupLinkRecord("First song", "First artist")),
            ),
        )
        val retrySnapshot = MaintenanceSnapshot(
            dbCleanup = MaintenanceDbCleanupState(
                timestamp = 2L,
                removed = 1,
                removedLinks = listOf(DbCleanupLinkRecord("Second song", "Second artist")),
            ),
        )
        val loadFailed = context.getString(R.string.common_load_failed)
        val retry = context.getString(R.string.common_action_retry)
        val refresh = context.getString(R.string.maintenance_refresh)
        val cleanup = context.getString(R.string.maintenance_db_cleanup)

        mockkStatic(::loadMaintenanceSnapshot)
        try {
            coEvery { loadMaintenanceSnapshot(any()) } returns firstSnapshot
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    MaintenanceSheet(showSheet = true, onDismissRequest = {})
                }
            }

            composeRule.onNodeWithText(cleanup).assertIsEnabled().performClick()
            composeRule.onNodeWithText("First song").assertExists()
            coEvery { loadMaintenanceSnapshot(any()) } throws IllegalStateException("Refresh failed")
            composeRule.onNodeWithContentDescription(refresh).performSemanticsAction(SemanticsActions.OnClick)

            composeRule.waitForIdle()
            coVerify(exactly = 2) { loadMaintenanceSnapshot(any()) }
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(retry).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText(loadFailed).assertExists()
            composeRule.onNodeWithText(cleanup).assertIsEnabled()
            composeRule.onNodeWithText("First song").assertExists()
            coEvery { loadMaintenanceSnapshot(any()) } returns retrySnapshot
            composeRule.onNodeWithText(retry).performSemanticsAction(SemanticsActions.OnClick)

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText("Second song").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("First song").assertDoesNotExist()
            composeRule.onNodeWithText(retry).assertDoesNotExist()
            coVerify(exactly = 3) { loadMaintenanceSnapshot(any()) }
        } finally {
            unmockkStatic(::loadMaintenanceSnapshot)
        }
    }
}
