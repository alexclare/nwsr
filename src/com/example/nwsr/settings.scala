package com.example.nwsr

import android.app.Activity
import android.content.Intent
import android.preference.Preference
import android.preference.PreferenceActivity
import android.os.Bundle
import android.widget.ScrollView
import android.webkit.WebView

class NWSRSettings extends PreferenceActivity {
  activity =>

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.settings);
    findPreference("settings_license")
    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      def onPreferenceClick(p: Preference): Boolean = {
        startActivity(new Intent(activity, classOf[NWSRLicense]))
        true
      }
    })
  }
}

class NWSRLicense extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val view = new ScrollView(this)
    val wv = new WebView(this)
    view.addView(wv)
    setContentView(view)
    wv.loadUrl("file:///android_asset/license.html")
  }
}
