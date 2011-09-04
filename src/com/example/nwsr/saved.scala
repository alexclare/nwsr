package com.example.nwsr

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.TextView

import scala.collection.mutable.ListBuffer

class NWSRSaved extends DatabaseActivity {
  val DeleteAll: Int = 2

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
        if (cursor.getCount > 0) {
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
        }
        true
      }
      case R.id.delete => {
        if (cursor.getCount > 0) {
          showDialog(DeleteAll)
        }
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

  override def onCreateDialog(id: Int): Dialog = id match {
    case DeleteAll => {
      val builder = new AlertDialog.Builder(this)
      builder.setMessage(R.string.dialog_sure)
      builder.setPositiveButton(
        R.string.dialog_yes, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, button: Int) {
            db.deleteSaved(None)
            updateView()
            dialog.dismiss()
          }
        })
      builder.setNegativeButton(
        R.string.dialog_no, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, button: Int) {
          dialog.dismiss()
        }
      })
      builder.setTitle(R.string.menu_delete_all)
      builder.create()
    }
    case _ => super.onCreateDialog(id)
  }
}

