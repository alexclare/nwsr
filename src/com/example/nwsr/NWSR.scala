package com.example.nwsr

import android.app.ListActivity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.SimpleCursorAdapter

class NWSR extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.headlines);

    db = new NWSRDatabase(this)
    cursor = db.stories()
    adapter = new SimpleCursorAdapter(
      this, R.layout.headline, cursor, Array("title", "link"),
      Array(R.id.headline_title, R.id.headline_link))
    setListAdapter(adapter)
  }

  override def onResume() {
    super.onResume()
    cursor.requery()
    adapter.notifyDataSetChanged()
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
