package com.example.nwsr

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException

import org.xml.sax.SAXParseException

import com.example.util.Feed
import com.example.util.NotFeedException

class NWSRRefreshService extends IntentService ("NWSRRefreshService") {
  var db: NWSRDatabase = _

  override def onCreate() {
    super.onCreate()
    db = new NWSRDatabase(this).open()
  }

  override def onDestroy() {
    super.onDestroy()
    db.close()
  }

  override def onHandleIntent(intent: Intent) {
    val cursor = db.feedsToRefresh()
    cursor.moveToFirst()
    while(!cursor.isAfterLast) {
      try {
        Feed.refresh(
          cursor.getString(1),
          cursor.getString(2) match {
            case null => None
            case e => Some(e)
          },
          cursor.getString(3) match {
            case null => None
            case l => Some(l)
          }) match {
          case Some(f) => db.addFeed(f, Some(cursor.getLong(0)))
          case None =>
        }
      } catch {
        case _ @ (_: FileNotFoundException | _: UnknownHostException |
                  _: IOException) => 
        case _ @ (_: SAXParseException | _: NotFeedException) => 
      }
      cursor.moveToNext()
    }
    cursor.close()
  }
}


class NWSRRefreshReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent) {
    context.startService(new Intent(context, classOf[NWSRRefreshService]))
  }
}
