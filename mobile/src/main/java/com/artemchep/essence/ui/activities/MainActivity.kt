package com.artemchep.essence.ui.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.graphics.luminance
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.artemchep.config.Config
import com.artemchep.essence.ACTION_PERMISSIONS_CHANGED
import com.artemchep.essence.Cfg
import com.artemchep.essence.R
import com.artemchep.essence.RUNTIME_PERMISSIONS
import com.artemchep.essence.adapters.ComplicationsPortImpl
import com.artemchep.essence.adapters.EssentialsPortImpl
import com.artemchep.essence.domain.adapters.weather.WeatherPort
import com.artemchep.essence.domain.models.OkScreen
import com.artemchep.essence.domain.models.SETTINGS_ITEM_ACCENT
import com.artemchep.essence.domain.models.SETTINGS_ITEM_THEME
import com.artemchep.essence.domain.viewmodel.SettingsViewModel
import com.artemchep.essence.domain.viewmodel.WatchFaceViewModel
import com.artemchep.essence.ui.adapters.MainAdapter
import com.artemchep.essence.ui.dialogs.AboutDialog
import com.artemchep.essence.ui.dialogs.PickerDialog
import com.artemchep.essence.ui.drawables.CircleDrawable
import com.artemchep.essence.ui.interfaces.OnItemClickListener
import com.artemchep.essence.ui.model.ConfigItem
import com.artemchep.essence.ui.views.WatchFaceView
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_toolbar.*
import kotlinx.coroutines.launch

/**
 * @author Artem Chepurnoy
 */
class MainActivity : ActivityBase(),
    PickerDialog.PickerDialogCallback,
    OnItemClickListener<ConfigItem>,
    View.OnClickListener,
    Config.OnConfigChangedListener<String> {

    // ---- Ports ----

    // The essentials of the watch face
    // engine.
    private val essentialsPort = EssentialsPortImpl(this)

    private val complicationsPort = ComplicationsPortImpl(this)

    private val weatherPort = WeatherPort()

    // ---- Setup ----

    private lateinit var dataClient: DataClient

    private lateinit var adapter: MainAdapter

    private lateinit var watchFaceView: WatchFaceView

    private lateinit var watchFaceViewModel: WatchFaceViewModel

    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dataClient = Wearable.getDataClient(this)

        setupView()
        setupRuntimePermissions()
    }

    private fun setupView() {
        actionAboutBtn.setOnClickListener(this)

        adapter = MainAdapter(mutableListOf()).apply {
            // Handle item click
            onItemClickListener = this@MainActivity
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        watchFaceView = findViewById(R.id.watchFaceView)

        setupWatchFaceViewModel()
        setupSettingsViewModel()
    }

    private fun setupWatchFaceViewModel() {
        val watchFaceViewModelFactory = WatchFaceViewModel.Factory(
            application, Cfg,
            complicationsPort,
            weatherPort,
            essentialsPort
        )
        watchFaceViewModel = ViewModelProviders
            .of(this, watchFaceViewModelFactory)
            .get(WatchFaceViewModel::class.java)
        watchFaceViewModel.setup()
    }

    private fun WatchFaceViewModel.setup() {
        timeLiveData.observe(this@MainActivity, Observer(watchFaceView::setTime))
        weatherLiveData.observe(this@MainActivity, Observer(watchFaceView::setWeather))
        visibilityLiveData.observe(this@MainActivity, Observer(watchFaceView::setVisibility))
        complicationsLiveData.observe(this@MainActivity, Observer(watchFaceView::setComplications))
        themeLiveData.observe(this@MainActivity, Observer { theme ->
            // Get the background color from a theme and set it
            // separately from a theme.
            val backgroundColor = theme.backgroundColor
            watchFaceView.apply {
                // Set theme
                setTheme(theme.copy(backgroundColor = Color.TRANSPARENT))
                // Set a background
                val bg = background as? CircleDrawable ?: CircleDrawable().also(::setBackground)
                bg.color = backgroundColor
            }
        })
    }

    private fun setupSettingsViewModel() {
        val settingsViewModelFactory = SettingsViewModel.Factory(
            application, Cfg,
            setOf(
                SETTINGS_ITEM_THEME,
                SETTINGS_ITEM_ACCENT
            )
        )
        settingsViewModel = ViewModelProviders
            .of(this, settingsViewModelFactory)
            .get(SettingsViewModel::class.java)
        settingsViewModel.setup()
    }

    private fun SettingsViewModel.setup() {
        screenLiveData.observe(this@MainActivity, Observer { screen ->
            when (screen) {
                is OkScreen<List<ConfigItem>> -> {
                    adapter.apply {
                        models.apply {
                            clear()
                            addAll(screen.data)
                        }
                        notifyDataSetChanged()
                    }
                }
            }
        })
        showPickerEvent.observe(this@MainActivity, Observer { event ->
            val data = event.consume()
            if (data != null) {
                val dialog = PickerDialog.create(data)
                dialog.show(supportFragmentManager, PickerDialog.TAG)
            }
        })
    }

    private fun setupRuntimePermissions() {
        val listener = object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                val localBroadcastManager = LocalBroadcastManager.getInstance(this@MainActivity)
                val intent = Intent(ACTION_PERMISSIONS_CHANGED)
                localBroadcastManager.sendBroadcast(intent)
            }

            override fun onPermissionRationaleShouldBeShown(
                permissions: MutableList<PermissionRequest>,
                token: PermissionToken
            ) {
                token.continuePermissionRequest()
            }
        }

        Dexter
            .withActivity(this)
            .withPermissions(RUNTIME_PERMISSIONS)
            .withListener(listener)
            .check()
    }

    override fun onStart() {
        super.onStart()

        // Setup the essentials: clock and an ambient mode
        // toggle.
        launch {
            with(essentialsPort) { setup() }
        }

        // Setup the complications.
        launch {
            with(complicationsPort) { setup() }
        }
    }

    override fun onResume() {
        super.onResume()
        Cfg.observe(this)
        updateAppAccentColor()
    }

    override fun onPause() {
        Cfg.removeObserver(this)
        super.onPause()
    }

    override fun onConfigChanged(keys: Set<String>) {
        if (Cfg.KEY_ACCENT_COLOR in keys) {
            updateAppAccentColor()
        }
    }

    private fun updateAppAccentColor(accentColor: Int = Cfg.accentColor) {
        val isContentColorDark = accentColor.luminance < 0.5
        val contentColor = if (isContentColorDark) {
            Color.WHITE
        } else Color.BLACK

        // Change the color of toolbar
        appbarView.setBackgroundColor(accentColor)
        titleTextView.setTextColor(contentColor)
        actionAboutBtn.imageTintList = ColorStateList.valueOf(contentColor)

        // Change the color of status bar
        window.apply {
            statusBarColor = accentColor
            val visibility = if (isContentColorDark) {
                decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            decorView.systemUiVisibility = visibility
        }
    }

    override fun onSingleItemPicked(requestCode: Int, key: String?) {
        settingsViewModel.result(requestCode, key)
    }

    override fun onItemClick(view: View, model: ConfigItem) {
        settingsViewModel.showDetails(model)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.actionAboutBtn -> {
                val dialog = AboutDialog()
                dialog.show(supportFragmentManager, AboutDialog.TAG)
            }
        }
    }

}
