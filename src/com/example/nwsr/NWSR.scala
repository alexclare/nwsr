package com.example.nwsr

import android.app.ListActivity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.Menu
import android.view.MenuItem

class NWSR extends ListActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.headlines);
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.headlines, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = 
    item.getItemId() match {
      case R.id.refresh => true
      case R.id.feeds => {
        startActivity(new Intent(this, classOf[NWSRFeeds]))
        true
      }
      case R.id.settings => {
        startActivity(new Intent(this, classOf[NWSRSettings]))
        true
      }
      case _ => super.onOptionsItemSelected(item)
    }
}

class NWSRSettings extends PreferenceActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.settings);
  }
}
