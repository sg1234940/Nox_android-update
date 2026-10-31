package pl.iskra.avatar.test

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.*
import pl.iskra.avatar.AvatarController
import pl.iskra.avatar.AvatarSurfaceView
import kotlin.math.roundToInt

/** Offline laboratory. Buttons simulate conversation events; no production chat adapter is enabled. */
class AvatarTestActivity : Activity() {
    private lateinit var avatar: AvatarSurfaceView
    private lateinit var lab: AvatarLabControls
    private lateinit var bodySelector: Spinner
    private var cameraSlider: SeekBar? = null
    private lateinit var status: TextView
    private lateinit var phase: TextView
    private lateinit var speech: TextView
    private val sliders = mutableListOf<SeekBar>()
    private val morphSliders = mutableMapOf<String, SeekBar>()
    private var needsNewTurn = false
    private var seq = 0L
    private var turn = 0L
    private var playback = 0L
    private var currentPlayback: String? = null
    private var speechPosition = 0L
    private var angle = 0f
    private var closeup = true
    private var refreshing = false
    private val controller get() = avatar.controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        avatar = AvatarSurfaceView(this)
        controller.bindConversation("avatar-lab"); controller.beginTurn(turn)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(15, 21, 32))
            setPadding(dp(12), dp(16), dp(12), dp(12))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(12), dp(12) + insets.systemWindowInsetTop, dp(12), dp(8) + insets.systemWindowInsetBottom); insets
            }
        }
        root.addView(label("Iskra · laboratorium awatara", 21f))
        root.addView(avatar, LinearLayout.LayoutParams(-1, 0, 1.1f))
        status = label("Ładowanie…"); root.addView(status)
        avatar.onStatus = { status.text = it }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(controls) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        lab = AvatarLabControls(this, avatar,
            begin = { controller.beginTurn(++turn); seq = 0; currentPlayback = null },
            send = ::send, camera = { angle to closeup },
            setCamera = { a, f -> angle = a; closeup = f; avatar.setCamera(a, f) }, refresh = ::refresh)
        lab.install(controls)
        controls.addView(label("Ręczne testy · suwak przejmuje sterowanie od Iskry", 15f))
        phase = label(""); controls.addView(phase)
        for ((title, p) in listOf("Spoczynek" to AvatarController.Phase.IDLE, "Słucha" to AvatarController.Phase.LISTENING,
            "Myśli" to AvatarController.Phase.THINKING)) {
            controls.addView(button(title) { currentPlayback = null; send(AvatarController.Command.PhaseChanged(p)); refresh() })
        }
        controls.addView(button("Mówi — rozpocznij symulację") {
            currentPlayback = "demo-${++playback}"; speechPosition = 0
            send(AvatarController.Command.SpeechStarted(currentPlayback!!)); refresh()
        })
        controls.addView(button("Przerwij / stop mowy") {
            send(AvatarController.Command.Interrupted); currentPlayback = null; refresh()
        })
        controls.addView(button("Nowa tura — odrzuć stare zdarzenia") {
            controller.beginTurn(++turn); seq = 0; currentPlayback = null; refresh()
        })
        speech = label("Przybliżenie ust z energii — bez dźwięku i rozpoznawania fonemów"); controls.addView(speech)
        addSlider(controls, "Energia symulowanej mowy", null) { value ->
            currentPlayback?.let { id ->
                speechPosition += 20
                send(AvatarController.Command.SpeechFrame(id, speechPosition, value))
            }
        }
        controls.addView(label("Animacje ciała", 18f))
        bodySelector = Spinner(this)
        val spinner = bodySelector
        val names = listOf("Poza bazowa · bez ruchu") + AvatarController.ANIMATIONS
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.setSelection(0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != bodySelector.selectedItemPosition) return
                val selected = names[position].takeIf { position > 0 }
                if (controller.snapshot().animation != selected) send(AvatarController.Command.Animation(selected))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        controls.addView(spinner)
        controls.addView(button("Zamroź / wznów bieżącą pozę") {
            send(AvatarController.Command.BodyPaused(!controller.snapshot().bodyPaused))
        })
        controls.addView(button("Twarz / cała postać") { closeup = !closeup; avatar.setCamera(angle, closeup) })
        addSlider(controls, "Obrót kamery", .5f) { angle = (it - .5f) * 2.4f; avatar.setCamera(angle, closeup) }
        controls.addView(label("Mimika · pełny zakres testowy 0–1", 18f))
        controls.addView(label("Wargi są rozdzielone i mają wnętrze. Bez zębów; to nadal test, nie gotowy lip-sync."))
        controls.addView(button("Poza bazowa i wyzeruj mimikę") {
            send(AvatarController.Command.Reset); currentPlayback = null
            refreshing = true; sliders.forEach { it.progress = 0 }; refreshing = false
            spinner.setSelection(0); refresh()
        })
        for (name in AvatarController.MORPHS) {
            addSlider(controls, name, 0f, tracked = true) { send(AvatarController.Command.Weight(name, it)) }
        }
        refresh()
        setContentView(root)
        root.requestApplyInsets()
    }
    private fun send(c: AvatarController.Command) {
        if (::lab.isInitialized) lab.manual()
        val result = controller.dispatch(AvatarController.Event("avatar-lab", turn, seq++, c))
        if (result != AvatarController.Result.APPLIED) status.text = "Zdarzenie: $result"
        if (::phase.isInitialized) refresh()
    }
    private fun refresh() {
        val s = controller.snapshot()
        val motion = if (s.animation == null) "poza bazowa" else if (s.bodyPaused) "zamrożona poza" else "ruch ciała"
        phase.text = "Stan: ${s.phase} · $motion · tura $turn"
        if (::bodySelector.isInitialized) {
            val position = s.animation?.let { AvatarController.ANIMATIONS.indexOf(it) + 1 } ?: 0
            if (bodySelector.selectedItemPosition != position) bodySelector.setSelection(position)
        }
        cameraSlider?.progress = ((angle / 2.4f + .5f) * 100).roundToInt().coerceIn(0,100)
        for ((name, bar) in morphSliders) bar.progress = (s.weights[AvatarController.MORPHS.indexOf(name)] * 100).roundToInt()
    }
    private fun addSlider(parent: LinearLayout, name: String, initial: Float?, tracked: Boolean = false, change: (Float) -> Unit) {
        val caption = label("$name · ${((initial ?: 0f) * 100).roundToInt()}%")
        parent.addView(caption)
        val bar = SeekBar(this).apply { max = 100; progress = ((initial ?: 0f) * 100).roundToInt(); contentDescription = name }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                caption.text = "$name · $progress%"
                if (fromUser && !refreshing) { if (::lab.isInitialized) lab.manual(); change(progress / 100f) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        parent.addView(bar, LinearLayout.LayoutParams(-1, dp(48)))
        if (name == "Obrót kamery") cameraSlider = bar
        if (tracked) { sliders += bar; morphSliders[name] = bar }
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; setOnClickListener { if (::lab.isInitialized) lab.manual(); action() } }
    private fun label(text: String, size: Float = 13f) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(227, 235, 248)); setPadding(0, dp(5), 0, dp(5)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    override fun onResume() {
        super.onResume()
        if (needsNewTurn) { controller.beginTurn(++turn); seq = 0; needsNewTurn = false; refresh() }
        avatar.setResumed(true)
        if (::lab.isInitialized) lab.resumed(true)
    }
    override fun onPause() { if (::lab.isInitialized) lab.resumed(false); avatar.setResumed(false); currentPlayback = null; needsNewTurn = true; super.onPause() }
    override fun onDestroy() { if (::lab.isInitialized) lab.destroy(); super.onDestroy() }
}
