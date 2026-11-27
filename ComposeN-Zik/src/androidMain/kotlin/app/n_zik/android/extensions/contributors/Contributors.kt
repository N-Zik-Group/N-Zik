package app.n_zik.android.extensions.contributors

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import com.google.gson.Gson
import com.google.gson.JsonArray
import app.n_zik.android.extensions.contributors.models.Developer
import app.n_zik.android.extensions.contributors.models.Translator
import timber.log.Timber

private val GSON = Gson()
private lateinit var developersList: List<Developer>
private lateinit var translatorsList: List<Translator>


@Composable
fun countDevelopers(): Int = countDevelopers(LocalContext.current)

internal fun countDevelopers(context: Context): Int = developers(context).size

@Synchronized
private fun developers(context: Context): List<Developer> {
    if (!::developersList.isInitialized) {
        developersList = try {
            val json = context.resources.openRawResource(R.raw.contributors).bufferedReader().use {
                GSON.fromJson(it, JsonArray::class.java)
            }

            json.map { GSON.fromJson(it, Developer::class.java) }.sortedBy { it.username }
        } catch (e: Exception) {
            Timber.tag("Contributors").e(e, "Unable to load developers")
            emptyList()
        }
    }
    return developersList
}

@Composable
fun ShowDevelopers() {
    val developers = developers(LocalContext.current)
    if (developers.isEmpty()) return

    Box( Modifier.fillMaxWidth().height( 600.dp ) ) {
        LazyColumn( Modifier.fillMaxWidth().padding( top = 15.dp ) ) {
            items(developers, key = { it.id }, contentType = { "developer" }) { it.Draw() }
        }
    }
}


@Synchronized
private fun translators(context: Context): List<Translator> {
    if (!::translatorsList.isInitialized) {
        translatorsList = try {
            val json = context.resources.openRawResource(R.raw.translators).bufferedReader().use {
                GSON.fromJson(it, JsonArray::class.java)
            }

            json.map { GSON.fromJson(it, Translator::class.java) }.sortedBy { it.displayName }
        } catch (e: Exception) {
            Timber.tag("Contributors").e(e, "Unable to load translators")
            emptyList()
        }
    }
    return translatorsList
}

@Composable
fun countTranslators(): Int = countTranslators(LocalContext.current)

internal fun countTranslators(context: Context): Int = translators(context).size

@Composable
fun ShowTranslators() {
    val translators = translators(LocalContext.current)
    if (translators.isEmpty()) return

    Box( Modifier.fillMaxWidth().height( 600.dp ) ) {
        LazyColumn( Modifier.fillMaxWidth().padding( top = 15.dp ) ) {
            items(translators, key = { it.username }, contentType = { "translator" }) { it.Draw() }
        }
    }
}


