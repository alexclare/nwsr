package com.example.nwsr

import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class NWSR extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: HeadlineListAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.headlines);

    db = new NWSRDatabase(this)
    cursor = db.stories()
    adapter = new HeadlineListAdapter(this, cursor)
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

class HeadlineListAdapter (context: Context, cursor: Cursor) extends BaseAdapter {
  val inflater = LayoutInflater.from(context)

  case class HeadlineView(title: TextView, link: TextView)

  override def getCount(): Int = {
    val cCount = cursor.getCount
    if (cCount == 0) 0 else (1 + cCount)
  }

  override def getItem(position: Int): Object = {
    if (cursor.moveToPosition(position))
      (cursor.getString(1), cursor.getString(2))
    else null
  }

  override def getItemId(position: Int): Long = {
    if (cursor.moveToPosition(position))
      cursor.getLong(0)
    else -1
  }

  override def getView(position: Int, convertView: View,
                       parent: ViewGroup): View = {
    if (position >= cursor.getCount) {
      inflater.inflate(R.layout.button_next_headline, null)
    } else if (convertView == null || convertView.getTag == null) {
      val view = inflater.inflate(R.layout.headline, null)
      val hv = HeadlineView(
        view.findViewById(R.id.headline_title).asInstanceOf[TextView],
        view.findViewById(R.id.headline_link).asInstanceOf[TextView])
      val text = getItem(position).asInstanceOf[Tuple2[String,String]]
      hv.title.setText(text._1)
      hv.link.setText(text._2)
      view.setTag(hv)
      view
    } else {
      val hv = convertView.getTag.asInstanceOf[HeadlineView]
      val text = getItem(position).asInstanceOf[Tuple2[String,String]]
      hv.title.setText(text._1)
      hv.link.setText(text._2)
      convertView
    }
  }
}

class NWSRSettings extends PreferenceActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.settings);
  }
}
