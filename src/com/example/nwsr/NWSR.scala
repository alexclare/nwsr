package com.example.nwsr

import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.TextView

import android.widget.CursorAdapter

// Move all database management (i.e. cursor stuff) to the Adapter class?
class NWSR extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: HeadlineListAdapter = _
  //val footer: TextView = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setTitle(R.string.title_headlines)
    setContentView(R.layout.headlines)
    PreferenceManager.setDefaultValues(this, R.xml.settings, false)

    /*
    val inflater = LayoutInflater.from(this)
    footer = inflater.inflate(R.layout.button_next_headline, getListView)
    */

    db = new NWSRDatabase(this)
    cursor = db.stories()
    adapter = new HeadlineListAdapter(this, cursor)
    setListAdapter(adapter)
    registerForContextMenu(getListView)
    getListView.setOnItemClickListener(
      new AdapterView.OnItemClickListener() {
        def onItemClick(parent: AdapterView[_], view: View,
                        position: Int, id: Long) {
          if (id < 0) {
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
              db.filterStory(cursor.getLong(0), false)
              cursor.moveToNext()
            }
            cursor.requery()
            adapter.notifyDataSetChanged()
          }
        }
      })
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

  override def onCreateContextMenu(menu: ContextMenu, v: View,
                                   menuInfo: ContextMenu.ContextMenuInfo) {
    // Not a good solution; still highlights excluded items with long press
    super.onCreateContextMenu(menu, v, menuInfo)
    if (menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].id >= 0) {
      val inflater = getMenuInflater()
      inflater.inflate(R.menu.context_headlines, menu)
      menu.setHeaderTitle(
        menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].targetView
        .findViewById(R.id.headline_title).asInstanceOf[TextView].getText)
    }
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo().asInstanceOf[
      AdapterView.AdapterContextMenuInfo]
    item.getItemId() match {
      case R.id.open_browser => {
        val url = info.targetView.findViewById(R.id.headline_link)
          .asInstanceOf[TextView].getText.toString
        db.filterStory(info.id, true)
        val intent = new Intent(Intent.ACTION_VIEW)
          .setData(Uri.parse(if (url.startsWith("http://")) url
                             else "http://" + url))
        startActivity(intent)
        true
      }
      case _ => super.onContextItemSelected(item)
    }
  }

  override def onDestroy() {
    super.onDestroy()
    cursor.close()
    db.close()
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

