package ru.mar4elkin.leetcodeactivity

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.mar4elkin.leetcodeactivity.ui.theme.LeetcodeActivityWidgetTheme
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll

suspend fun refreshAllWidgets(context: Context) {
    ActivityWidget().updateAll(context)
}

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        refreshAllWidgets(applicationContext)
        return Result.success()
    }
}

class LeetcodeActivityWidget : Application() {
    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WidgetUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

val NICKNAME_KEY = stringPreferencesKey("nickname")
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeetcodeActivityWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        readNickname = {
                            dataStore.data.map { prefs -> prefs[NICKNAME_KEY] ?: "" }
                        },
                        writeNickname = { newValue ->
                            dataStore.edit { prefs -> prefs[NICKNAME_KEY] = newValue }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    readNickname: () -> kotlinx.coroutines.flow.Flow<String>,
    writeNickname: suspend (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val savedNickname by readNickname().collectAsState(initial = "")
    var nickname by remember(savedNickname) { mutableStateOf(savedNickname) }
    val context = LocalContext.current
    val appContext = context.applicationContext

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LeetCode Activity Widget",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Укажите ваш никнейм LeetCode. После сохранения виджет обновится автоматически.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Никнейм") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    val trimmed = nickname.trim()
                    writeNickname(trimmed)
                    refreshAllWidgets(appContext)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (savedNickname.isBlank())
                "Никнейм пока не задан"
            else
                "Текущий никнейм: $savedNickname",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
