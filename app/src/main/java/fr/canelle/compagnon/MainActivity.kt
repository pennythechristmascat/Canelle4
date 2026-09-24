package fr.canelle.compagnon

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * L'écran de Canelle : une page web locale (le visage, les sous-titres, le menu)
 * reliée au téléphone par le pont « Android ».
 */
class MainActivity : ComponentActivity(), ToolHost {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    /** Marges à laisser libres (encoche, barres système), en pixels CSS, pour la page. */
    private var insetsJs = ""
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var permWaiter: CompletableDeferred<Boolean>? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        permWaiter?.complete(res.values.any { it })
        permWaiter = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val firstLaunch = Store.visits == 0

        // Plein écran : la page dessine sous les barres système, qui restent cachées
        // (on peut les faire apparaître un instant en glissant depuis le bord de l'écran).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#040a13"))
        root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#040a13"))
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            // Le clavier réduit la page par le bas, pour que la zone de saisie reste visible.
            v.setPadding(0, 0, 0, ime.bottom)
            val d = resources.displayMetrics.density
            val top = maxOf(bars.top, cut.top) / d
            val bottom = if (ime.bottom > 0) 0f else maxOf(bars.bottom, cut.bottom) / d
            val left = maxOf(bars.left, cut.left) / d
            val right = maxOf(bars.right, cut.right) / d
            insetsJs = "(function(s){s.setProperty('--sat','${top}px');s.setProperty('--sab','${bottom}px');" +
                "s.setProperty('--sal','${left}px');s.setProperty('--sar','${right}px');})(document.documentElement.style)"
            web.evaluateJavascript(insetsJs, null)
            WindowInsetsCompat.CONSUMED
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.scheme == "file") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (insetsJs.isNotEmpty()) view.evaluateJavascript(insetsJs, null)
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRANCE
                tts?.setPitch(1.3f)
                tts?.setSpeechRate(1.05f)
                ttsReady = true
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                js("window.onSpeakDone&&onSpeakDone(${q(utteranceId ?: "")})")
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.onBack&&window.onBack())?1:0") { r ->
                    if (r != "1") moveTaskToBack(true)
                }
            }
        })

        if (firstLaunch && Build.VERSION.SDK_INT >= 33) {
            lifecycleScope.launch {
                delay(4000)
                requestPerms(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // En arrière-plan, tout s'arrête : position, mesure du rythme, animations et minuteries de la page.
        LocationKeeper.stop()
        MusicWatcher.release()
        BatteryWatch.uiAlert = null
        if (::web.isInitialized) {
            js("window.onPauseApp&&onPauseApp()")
            web.onPause()
            web.pauseTimers()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        LocationKeeper.start(this)
        BatteryWatch.uiAlert = { threshold, level -> js("window.onBatteryAlert&&onBatteryAlert($threshold,$level)") }
        if (::web.isInitialized) {
            web.onResume()
            web.resumeTimers()
            js("window.onResumeApp&&onResumeApp()")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun q(s: String): String = JSONObject.quote(s)

    fun js(code: String) {
        runOnUiThread { if (!isDestroyed) web.evaluateJavascript(code, null) }
    }

    // ---------------------------------------------------------------- autorisations

    suspend fun requestPerms(perms: Array<String>): Boolean = withContext(Dispatchers.Main) {
        if (perms.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) return@withContext true
        val waiter = CompletableDeferred<Boolean>()
        permWaiter = waiter
        permLauncher.launch(perms)
        waiter.await()
    }

    override suspend fun ensureLocationPermission(): Boolean =
        requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    // ---------------------------------------------------------------- reconnaissance vocale

    private fun listenNow() {
        runOnUiThread {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                lifecycleScope.launch {
                    if (requestPerms(arrayOf(Manifest.permission.RECORD_AUDIO))) listenNow()
                    else js("window.onSpeechError&&onSpeechError('permission')")
                }
                return@runOnUiThread
            }
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                js("window.onSpeechError&&onSpeechError('indisponible')")
                return@runOnUiThread
            }
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { js("window.onSpeechState&&onSpeechState(true)") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val code = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "rien"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "reseau"
                        else -> "erreur"
                    }
                    js("window.onSpeechState&&onSpeechState(false)")
                    js("window.onSpeechError&&onSpeechError(${q(code)})")
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    js("window.onSpeechState&&onSpeechState(false)")
                    js("window.onSpeechResult&&onSpeechResult(${q(t)})")
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    js("window.onSpeechPartial&&onSpeechPartial(${q(t)})")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            r.startListening(intent)
        }
    }

    // ---------------------------------------------------------------- pont avec la page

    inner class Bridge {

        @JavascriptInterface
        fun getState(): String = Store.uiState(LocalModel.isDownloaded(this@MainActivity)).toString()

        @JavascriptInterface
        fun saveUi(json: String) {
            runCatching {
                val o = JSONObject(json)
                Store.companionName = o.optString("companionName", Store.companionName).trim().take(20).ifBlank { "Canelle" }
                Store.sound = o.optString("sound", Store.sound)
                Store.visits = o.optInt("visits", Store.visits)
                Store.lastSeen = o.optLong("lastSeen", Store.lastSeen)
                if (o.has("hidePrivacy")) Store.hidePrivacy = o.optBoolean("hidePrivacy", Store.hidePrivacy)
                if (o.has("musicApp")) Store.musicApp = o.optString("musicApp", Store.musicApp)
                if (o.has("skin")) Store.skin = o.optString("skin", Store.skin)
            }
        }

        @JavascriptInterface
        fun addAssistantLines(json: String) {
            runCatching {
                val a = JSONArray(json)
                val texts = (0 until a.length()).map { a.getJSONObject(it).optString("text") }.filter { it.isNotBlank() }
                if (texts.isNotEmpty()) Store.addTurn("assistant", texts.joinToString(" "))
                texts.forEach { Store.addLog("bip", it) }
                LocalModel.markDirty()
            }
        }

        @JavascriptInterface
        fun forget() = Store.forget()

        @JavascriptInterface
        fun removeFact(text: String) {
            Store.removeFact(text)
            LocalModel.markDirty()
        }

        // ------------------------------------------------ codes d'accès

        /** "dev", "owner_name" (Canelle doit demander le nom) ou "invalid". */
        @JavascriptInterface
        fun checkCode(code: String): String = Access.check(code).also { LocalModel.markDirty() }

        @JavascriptInterface
        fun confirmOwner(name: String): Boolean = Access.confirmOwner(name).also { LocalModel.markDirty() }

        // ------------------------------------------------ musique

        /** { active, playing, title, artist, app, access } */
        @JavascriptInterface
        fun musicState(): String = MusicWatcher.state(this@MainActivity).toString()

        /** Intensité de la musique (0 à 1), ou -1 si le téléphone ne permet pas de la mesurer. */
        @JavascriptInterface
        fun musicLevel(): Float = MusicWatcher.level(this@MainActivity)

        @JavascriptInterface
        fun musicControl(action: String): Boolean = MusicWatcher.control(this@MainActivity, action)

        /** Joue une recherche dans Spotify ou Deezer : "ok:<appli>", "absent:<appli>" ou "aucune". */
        @JavascriptInterface
        fun musicPlay(app: String, query: String): String = MusicWatcher.play(this@MainActivity, app, query)

        @JavascriptInterface
        fun musicApps(): String = MusicWatcher.apps(this@MainActivity).toString()

        @JavascriptInterface
        fun openMusicAccess() = MusicWatcher.openTitleAccess(this@MainActivity)

        /** Vrai quand la voix de Canelle parle (pour ne pas la confondre avec de la musique). */
        @JavascriptInterface
        fun ttsSpeaking(): Boolean = tts?.isSpeaking == true

        /** Ferme l'application de force (Canelle chasse un faux gérant). */
        @JavascriptInterface
        fun closeApp() {
            runOnUiThread { finishAndRemoveTask() }
        }

        @JavascriptInterface
        fun clearRank() {
            Access.clear()
            LocalModel.markDirty()
        }

        @JavascriptInterface
        fun send(id: String, text: String) {
            lifecycleScope.launch {
                try {
                    val r = Brain.reply(
                        this@MainActivity, text, foreground = true, host = this@MainActivity,
                        onStatus = { st -> js("window.onReplyStatus&&onReplyStatus(${q(id)}, ${q(st)})") },
                        onLine = { line -> js("window.onReplyLine&&onReplyLine(${q(id)}, ${q(line.toJson().toString())})") }
                    )
                    js("window.onReply(${q(id)}, ${q(r.toJson().toString())})")
                } catch (e: BrainException) {
                    js("window.onReplyError(${q(id)}, ${q(e.code)}, ${q(e.message ?: "")})")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    js("window.onReplyError(${q(id)}, 'upstream', ${q(e.toString())})")
                }
            }
        }

        @JavascriptInterface
        fun battery(): String = Device.battery(this@MainActivity).toString()

        @JavascriptInterface
        fun listen() = listenNow()

        @JavascriptInterface
        fun stopListening() {
            runOnUiThread { recognizer?.stopListening() }
        }

        @JavascriptInterface
        fun hasTts(): Boolean = tts != null

        @JavascriptInterface
        fun speak(text: String, id: String) {
            val engine = tts
            if (!ttsReady || engine == null) {
                js("window.onSpeakDone&&onSpeakDone(${q(id)})")
                return
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }

        @JavascriptInterface
        fun stopSpeaking() {
            tts?.stop()
        }

        @JavascriptInterface
        fun openPlace(lat: Double, lon: Double, name: String) {
            runOnUiThread { Device.mapsPlace(this@MainActivity, lat, lon, name) }
        }

        @JavascriptInterface
        fun navigate(lat: Double, lon: Double) {
            runOnUiThread { Device.mapsNavigate(this@MainActivity, lat, lon) }
        }

        @JavascriptInterface
        fun mapsSearch(query: String) {
            runOnUiThread { Device.mapsSearch(this@MainActivity, query, Store.lastPosition()) }
        }

        // ------------------------------------------------ cerveau local

        @JavascriptInterface
        fun modelStatus(): String = LocalModel.status(this@MainActivity).toString()

        /** Renvoie "" si le téléchargement démarre, sinon un code d'erreur ("place", "indisponible"). */
        @JavascriptInterface
        fun startDownload(allowMobile: Boolean): String {
            Store.brainIntroShown = true
            return LocalModel.startDownload(this@MainActivity, allowMobile) ?: ""
        }

        @JavascriptInterface
        fun cancelDownload() = LocalModel.cancelDownload(this@MainActivity)

        @JavascriptInterface
        fun deleteModel() {
            lifecycleScope.launch { LocalModel.delete(this@MainActivity) }
        }

        /** Réveille le cerveau en arrière-plan, pour que la première réponse arrive plus vite. */
        @JavascriptInterface
        fun warmUp() {
            lifecycleScope.launch { runCatching { LocalModel.ensureLoaded(this@MainActivity) } }
        }

        @JavascriptInterface
        fun brainIntroShown(): Boolean = Store.brainIntroShown

        @JavascriptInterface
        fun setBrainIntroShown() {
            Store.brainIntroShown = true
        }

        // ------------------------------------------------ réglages

        @JavascriptInterface
        fun getSettings(): String {
            return JSONObject()
                .put("useGpu", Store.useGpu)
                .put("checkins", Store.checkins)
                .put("intervalHours", Store.intervalHours)
                .put("quietStart", Store.quietStart)
                .put("quietEnd", Store.quietEnd)
                .put("notifAllowed", Notifs.canPost(this@MainActivity))
                .toString()
        }

        @JavascriptInterface
        fun saveSettings(json: String) {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return
            if (o.has("useGpu")) {
                val gpu = o.optBoolean("useGpu", Store.useGpu)
                if (gpu != Store.useGpu || (gpu && Store.gpuBroken)) {
                    Store.useGpu = gpu
                    Store.gpuBroken = false
                    lifecycleScope.launch { LocalModel.release() }
                }
            }
            Store.checkins = o.optBoolean("checkins", Store.checkins)
            Store.intervalHours = o.optInt("intervalHours", Store.intervalHours).coerceIn(1, 24)
            Store.quietStart = o.optInt("quietStart", Store.quietStart).coerceIn(0, 23)
            Store.quietEnd = o.optInt("quietEnd", Store.quietEnd).coerceIn(0, 23)
            if (Store.checkins && Build.VERSION.SDK_INT >= 33) {
                lifecycleScope.launch { requestPerms(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
            }
        }

        @JavascriptInterface
        fun testCheckin() {
            lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= 33 && !requestPerms(arrayOf(Manifest.permission.POST_NOTIFICATIONS))) {
                    js("window.onNotifDenied&&onNotifDenied()")
                    return@launch
                }
                CheckinWorker.postCheckin(this@MainActivity)
            }
        }
    }
}
