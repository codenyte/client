package com.looker.droidify.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.looker.droidify.BuildConfig
import com.looker.droidify.R
import com.looker.core_common.R.string as stringRes
import com.looker.droidify.content.Preferences
import com.looker.droidify.databinding.PreferenceItemBinding
import com.looker.droidify.screen.ScreenFragment
import com.looker.droidify.utility.Utils.getLocaleOfCode
import com.looker.droidify.utility.Utils.languagesList
import com.looker.droidify.utility.Utils.translateLocale
import com.looker.droidify.utility.extension.resources.*
import com.looker.droidify.utility.extension.screenActivity
import kotlinx.coroutines.launch

class SettingsFragment : ScreenFragment() {

	private var preferenceBinding: PreferenceItemBinding? = null
	private val preferences = mutableMapOf<Preferences.Key<*>, Preference<*>>()
	private val viewModel by viewModels<SettingsViewModel>()

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		preferenceBinding = PreferenceItemBinding.inflate(layoutInflater)
		screenActivity.onToolbarCreated(toolbar)
		collapsingToolbar.title = getString(stringRes.settings)

		val content = fragmentBinding.fragmentContent
		val scroll = NestedScrollView(content.context)
		scroll.id = R.id.preferences_list
		scroll.isFillViewport = true
		content.addView(
			scroll,
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		)
		val scrollLayout = FrameLayout(content.context)
		scroll.addView(
			scrollLayout,
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		)
		val preferences = LinearLayout(scrollLayout.context)
		preferences.orientation = LinearLayout.VERTICAL
		scrollLayout.addView(
			preferences,
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		)

		preferences.addCategory(requireContext().getString(stringRes.prefs_personalization)) {
			addList(
				Preferences.Key.Language,
				context.getString(stringRes.prefs_language_title),
				languagesList
			) { translateLocale(context.getLocaleOfCode(it)) }
			addEnumeration(Preferences.Key.Theme, getString(stringRes.theme)) {
				when (it) {
					is Preferences.Theme.System -> getString(stringRes.system)
					is Preferences.Theme.AmoledSystem -> getString(stringRes.system) + " " + getString(
						stringRes.amoled
					)
					is Preferences.Theme.Light -> getString(stringRes.light)
					is Preferences.Theme.Dark -> getString(stringRes.dark)
					is Preferences.Theme.Amoled -> getString(stringRes.amoled)
				}
			}
			addSwitch(
				Preferences.Key.ListAnimation, getString(stringRes.list_animation),
				getString(stringRes.list_animation_description)
			)
		}
		preferences.addCategory(getString(stringRes.updates)) {
			addEnumeration(
				Preferences.Key.AutoSync,
				getString(stringRes.sync_repositories_automatically)
			) {
				when (it) {
					Preferences.AutoSync.Never -> getString(stringRes.never)
					Preferences.AutoSync.Wifi -> getString(stringRes.only_on_wifi)
					Preferences.AutoSync.Always -> getString(stringRes.always)
				}
			}
			addSwitch(
				Preferences.Key.UpdateNotify, getString(stringRes.notify_about_updates),
				getString(stringRes.notify_about_updates_summary)
			)
			addSwitch(
				Preferences.Key.UpdateUnstable, getString(stringRes.unstable_updates),
				getString(stringRes.unstable_updates_summary)
			)
			addSwitch(
				Preferences.Key.IncompatibleVersions, getString(stringRes.incompatible_versions),
				getString(stringRes.incompatible_versions_summary)
			)
		}
		preferences.addCategory(getString(stringRes.proxy)) {
			addEnumeration(Preferences.Key.ProxyType, getString(stringRes.proxy_type)) {
				when (it) {
					is Preferences.ProxyType.Direct -> getString(stringRes.no_proxy)
					is Preferences.ProxyType.Http -> getString(stringRes.http_proxy)
					is Preferences.ProxyType.Socks -> getString(stringRes.socks_proxy)
				}
			}
			addEditString(Preferences.Key.ProxyHost, getString(stringRes.proxy_host))
			addEditInt(Preferences.Key.ProxyPort, getString(stringRes.proxy_port), 1..65535)
		}
		preferences.addCategory(getString(stringRes.install_types)) {
			addEnumeration(
				Preferences.Key.InstallerType, getString(stringRes.installer),
				onClick = { viewModel.installerSelected(it) }
			) {
				when (it) {
					Preferences.InstallerType.Legacy -> getString(stringRes.legacy_installer)
					Preferences.InstallerType.Session -> getString(stringRes.session_installer)
					Preferences.InstallerType.Shizuku -> getString(stringRes.shizuku_installer)
					Preferences.InstallerType.Root -> getString(stringRes.root_installer)
				}
			}
		}
		preferences.addCategory(getString(stringRes.credits)) {
			addText(
				title = "Based on Foxy-Droid",
				summary = "FoxyDroid",
				url = "https://github.com/kitsunyan/foxy-droid/"
			)
			addText(
				title = getString(R.string.application_name),
				summary = "v ${BuildConfig.VERSION_NAME}",
				url = "https://github.com/iamlooker/Droid-ify/"
			)
		}

		lifecycleScope.launch {
			Preferences.subject
				.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
				.collect { updatePreference(it) }
		}
		updatePreference(null)
	}

	private fun LinearLayout.addText(title: String, summary: String, url: String) {
		val text = MaterialTextView(context)
		val subText = MaterialTextView(context)
		text.text = title
		subText.text = summary
		text.setTextSizeScaled(16)
		subText.setTextSizeScaled(14)
		resources.sizeScaled(16).let {
			text.setPadding(it, it, 5, 5)
			subText.setPadding(it, 5, 5, 25)
		}
		addView(
			text,
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
		addView(
			subText,
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
		text.setOnClickListener { openURI(url.toUri()) }
		subText.setOnClickListener { openURI(url.toUri()) }
	}

	private fun openURI(url: Uri) {
		startActivity(Intent(Intent.ACTION_VIEW, url))
	}

	override fun onDestroyView() {
		super.onDestroyView()
		preferences.clear()
		preferenceBinding = null
	}

	private fun updatePreference(key: Preferences.Key<*>?) {
		if (key != null) {
			preferences[key]?.update()
		}
		if (key == null || key == Preferences.Key.ProxyType) {
			val enabled = when (Preferences[Preferences.Key.ProxyType]) {
				is Preferences.ProxyType.Direct -> false
				is Preferences.ProxyType.Http, is Preferences.ProxyType.Socks -> true
			}
			preferences[Preferences.Key.ProxyHost]?.setEnabled(enabled)
			preferences[Preferences.Key.ProxyPort]?.setEnabled(enabled)
		}
		if (key == Preferences.Key.Theme) {
			requireActivity().recreate()
		}
	}

	private fun LinearLayout.addCategory(
		title: String,
		callback: LinearLayout.() -> Unit,
	) {
		val text = MaterialTextView(context)
		text.typeface = TypefaceExtra.medium
		text.setTextSizeScaled(14)
		text.setTextColor(text.context.getColorFromAttr(R.attr.colorPrimary))
		text.text = title
		resources.sizeScaled(16).let { text.setPadding(it, it, it, 0) }
		addView(
			text,
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
		callback()
	}

	private fun <T> LinearLayout.addPreference(
		key: Preferences.Key<T>, title: String,
		summaryProvider: () -> String, dialogProvider: ((Context) -> AlertDialog)?,
	): Preference<T> {
		val preference =
			Preference(key, this@SettingsFragment, this, title, summaryProvider, dialogProvider)
		preferences[key] = preference
		return preference
	}

	private fun LinearLayout.addSwitch(
		key: Preferences.Key<Boolean>,
		title: String,
		summary: String,
	) {
		val preference = addPreference(key, title, { summary }, null)
		preference.check.visibility = View.VISIBLE
		preference.view.setOnClickListener { Preferences[key] = !Preferences[key] }
		preference.setCallback { preference.check.isChecked = Preferences[key] }
	}

	private fun <T> LinearLayout.addEdit(
		key: Preferences.Key<T>, title: String, valueToString: (T) -> String,
		stringToValue: (String) -> T?, configureEdit: (TextInputEditText) -> Unit,
	) {
		addPreference(key, title, { valueToString(Preferences[key]) }) { it ->
			val scroll = NestedScrollView(it)
			scroll.resources.sizeScaled(20).let { scroll.setPadding(it, 0, it, 0) }
			val edit = TextInputEditText(it)
			configureEdit(edit)
			edit.id = android.R.id.edit
			edit.resources.sizeScaled(16)
				.let { edit.setPadding(edit.paddingLeft, it, edit.paddingRight, it) }
			edit.setText(valueToString(Preferences[key]))
			edit.hint = edit.text.toString()
			edit.text?.let { editable -> edit.setSelection(editable.length) }
			edit.requestFocus()
			scroll.addView(
				edit,
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
			MaterialAlertDialogBuilder(it)
				.setTitle(title)
				.setView(scroll)
				.setPositiveButton(stringRes.ok) { _, _ ->
					val value = stringToValue(edit.text.toString()) ?: key.default.value
					post { Preferences[key] = value }
				}
				.setNegativeButton(stringRes.cancel, null)
				.create()
				.apply {
					window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
				}
		}
	}

	private fun LinearLayout.addEditString(key: Preferences.Key<String>, title: String) {
		addEdit(key, title, { it }, { it }, { })
	}

	private fun LinearLayout.addEditInt(
		key: Preferences.Key<Int>,
		title: String,
		range: IntRange?,
	) {
		addEdit(key, title, { it.toString() }, { it.toIntOrNull() }) {
			it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
			if (range != null) it.filters =
				arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
					val value = (dest.substring(0, dstart) + source.substring(start, end) +
							dest.substring(dend, dest.length)).toIntOrNull()
					if (value != null && value in range) null else ""
				})
		}
	}

	private fun <T : Preferences.Enumeration<T>> LinearLayout.addEnumeration(
		key: Preferences.Key<T>,
		title: String,
		onClick: (T) -> Unit = {},
		valueToString: (T) -> String
	) {
		addPreference(key, title, { valueToString(Preferences[key]) }) {
			val values = key.default.value.values
			MaterialAlertDialogBuilder(it)
				.setTitle(title)
				.setSingleChoiceItems(
					values.map(valueToString).toTypedArray(),
					values.indexOf(Preferences[key])
				) { dialog, which ->
					dialog.dismiss()
					post {
						Preferences[key] = values[which]
						onClick(Preferences[key])
					}
				}
				.setNegativeButton(stringRes.cancel, null)
				.create()
		}
	}

	private fun <T> LinearLayout.addList(
		key: Preferences.Key<T>,
		title: String,
		values: List<T>,
		valueToString: (T) -> String,
	) {
		addPreference(key, title, { valueToString(Preferences[key]) }) {
			MaterialAlertDialogBuilder(it)
				.setTitle(title)
				.setSingleChoiceItems(
					values.map(valueToString).toTypedArray(),
					values.indexOf(Preferences[key])
				) { dialog, which ->
					dialog.dismiss()
					post { Preferences[key] = values[which] }
				}
				.setNegativeButton(stringRes.cancel, null)
				.create()
		}
	}

	private class Preference<T>(
		private val key: Preferences.Key<T>,
		fragment: Fragment,
		parent: ViewGroup,
		titleText: String,
		private val summaryProvider: () -> String,
		private val dialogProvider: ((Context) -> AlertDialog)?,
	) {
		val view = parent.inflate(R.layout.preference_item)
		val title = view.findViewById<MaterialTextView>(R.id.title)!!
		val summary = view.findViewById<MaterialTextView>(R.id.summary)!!
		val check = view.findViewById<SwitchMaterial>(R.id.check)!!

		private var callback: (() -> Unit)? = null

		init {
			title.text = titleText
			parent.addView(view)
			if (dialogProvider != null) {
				view.setOnClickListener {
					PreferenceDialog(key.name)
						.show(
							fragment.childFragmentManager,
							"${PreferenceDialog::class.java.name}.${key.name}"
						)
				}
			}
			update()
		}

		fun setCallback(callback: () -> Unit) {
			this.callback = callback
			update()
		}

		fun setEnabled(enabled: Boolean) {
			view.isEnabled = enabled
			title.isEnabled = enabled
			summary.isEnabled = enabled
			check.isEnabled = enabled
		}

		fun update() {
			summary.text = summaryProvider()
			summary.visibility = if (summary.text.isNotEmpty()) View.VISIBLE else View.GONE
			callback?.invoke()
		}

		fun createDialog(context: Context): AlertDialog {
			return dialogProvider!!(context)
		}
	}

	class PreferenceDialog() : DialogFragment() {
		companion object {
			private const val EXTRA_KEY = "key"
		}

		constructor(key: String) : this() {
			arguments = Bundle().apply {
				putString(EXTRA_KEY, key)
			}
		}

		override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
			val preferences = (parentFragment as SettingsFragment).preferences
			val key = requireArguments().getString(EXTRA_KEY)!!
				.let { name -> preferences.keys.find { it.name == name }!! }
			val preference = preferences[key]!!
			return preference.createDialog(requireContext())
		}
	}
}
