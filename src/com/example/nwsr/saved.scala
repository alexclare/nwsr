package com.example.nwsr

import android.os.Bundle
import android.content.Intent
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.TextView

import scala.collection.mutable.ListBuffer

class NWSRSaved extends DatabaseActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.saved)
    
    registerForContextMenu(getListView)
    cursor = db.savedView()
    adapter = new SimpleCursorAdapter(
      this, R.layout.headline, cursor, Array("title", "link"),
      Array(R.id.headline_title, R.id.headline_link))
    setListAdapter(adapter)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.saved, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean =
    item.getItemId match {
      case R.id.share => {
        val text = {
          val result = ListBuffer.empty[String]
          cursor.moveToFirst()
          while (!cursor.isAfterLast) {
            result.append(cursor.getString(2))
            cursor.moveToNext()
          }
          result.mkString(";")
        }
        val intent = new Intent(Intent.ACTION_SEND)
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(intent, "Share via"))
        true
      }
      case R.id.delete => {
        db.deleteSaved(None)
        updateView()
        true
      }
    }

  override def onCreateContextMenu(menu: ContextMenu, v: View,
                                   menuInfo: ContextMenu.ContextMenuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo)
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.context_saved, menu)
    menu.setHeaderTitle(
      menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].targetView
      .findViewById(R.id.headline_title).asInstanceOf[TextView].getText)
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo.asInstanceOf[
      AdapterView.AdapterContextMenuInfo]
    item.getItemId match {
      case R.id.open_browser => {
        openInBrowser(info.targetView.findViewById(R.id.headline_link)
                      .asInstanceOf[TextView].getText.toString)
        true
      }
      case R.id.delete => {
        db.deleteSaved(Some(info.id))
        updateView()
        true
      }
      case _ => super.onContextItemSelected(item)
    }
  }
}

