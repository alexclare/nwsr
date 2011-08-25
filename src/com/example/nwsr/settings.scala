package com.example.nwsr

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Intent
import android.preference.Preference
import android.preference.PreferenceActivity
import android.os.AsyncTask
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

import scala.io.Source

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
    val tv = new TextView(this)
    view.addView(tv)
    setContentView(view)
    new AsyncTask[Object, Unit, String]() {
      override def onPreExecute() {
        showDialog(0)
      }

      // doInBackground throws an exception when given the param type Unit*
      def doInBackground(a: Object*): String = {
        Source.fromInputStream(
          getResources().openRawResource(R.raw.license))
          .getLines().mkString("\n")
      }

      override def onPostExecute(text: String) {
        tv.setText(text)
        dismissDialog(0)
      }
    }.execute()
  }

  override def onCreateDialog(id: Int): Dialog = ProgressDialog.show(
    this, "", "Loading...", true)
}
