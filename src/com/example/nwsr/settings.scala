package com.example.nwsr

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
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

    findPreference("feed_refresh_rate")
    .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
      val manager = activity.getSystemService(Context.ALARM_SERVICE)
        .asInstanceOf[AlarmManager]
      val service = PendingIntent.getService(
        activity, 0, new Intent(activity, classOf[NWSRRefreshService]),
        PendingIntent.FLAG_UPDATE_CURRENT)
      val interval = System.currentTimeMillis + 3600000

      def onPreferenceChange(p: Preference, value: Object): Boolean = {
        manager.cancel(service)
        value.asInstanceOf[String] match {
          case "1" => manager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, interval,
            AlarmManager.INTERVAL_HOUR, service)
          case "2" => manager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, interval,
            AlarmManager.INTERVAL_HALF_DAY, service)
          case "3" => manager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, interval,
            AlarmManager.INTERVAL_DAY, service)
          case _ => 
        }
        true
      }
    })

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
